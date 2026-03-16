# -*- coding: utf-8 -*-
"""
LLM Service — orchestrates AI chat with Foggy MCP tool calling.

Flow:
    1. Build system prompt with available models
    2. Call LLM via litellm (OpenAI-compatible, supports 100+ providers)
    3. If LLM returns tool_use → execute through Foggy (with permission injection)
    4. Feed tool results back to LLM
    5. Return final assistant response

Supported providers (via litellm):
    - OpenAI (gpt-4o, gpt-4o-mini)
    - Anthropic (claude-3-5-sonnet, claude-3-haiku)
    - DeepSeek (deepseek/deepseek-chat)
    - Ollama (ollama/llama3, ollama/qwen2)
    - Azure, Groq, Together, etc.
"""
import json
import logging

_logger = logging.getLogger(__name__)

# Maximum tool calling rounds to prevent infinite loops
MAX_TOOL_ROUNDS = 5

# ── System prompt template ──────────────────────────────────────

SYSTEM_PROMPT_TEMPLATE = """You are Foggy AI, a business data analyst embedded in Odoo ERP.
You help users analyze their business data using natural language.

## Available Data Models
{model_descriptions}

## How to Query
Use the `dataset.query_model` tool to run data queries. Key parameters:
- **model**: The query model name (see available models above)
- **payload**: The DSL query payload with columns, filters, sorting, and pagination

## Query Payload Format
```json
{{
  "columns": ["field1", "field2"],
  "columnSort": [{{"column": "field1", "order": "desc"}}],
  "slice": [{{"field": "fieldName", "op": "=", "value": "..."}}, ...],
  "pageSize": 20,
  "pageIndex": 0
}}
```

## Guidelines
- Always specify which columns to return (don't leave columns empty)
- Use dimension$id for filtering, dimension$caption for display
- For date filtering, use ISO format: "2025-01-01"
- Measures (numeric fields) are auto-aggregated when dimensions are present
- Respond in the user's language (Chinese if they write in Chinese)
- Present data in clear markdown tables when appropriate
- Provide brief analysis and insights along with the data
"""


def _get_llm_config(env):
    """Read LLM configuration from Odoo settings."""
    get = env['ir.config_parameter'].sudo().get_param
    provider = get('foggy_mcp.llm_provider', 'openai')
    api_key = get('foggy_mcp.llm_api_key', '')
    model = get('foggy_mcp.llm_model', 'gpt-4o-mini')
    base_url = get('foggy_mcp.llm_base_url', '')
    temperature = float(get('foggy_mcp.llm_temperature', '0.3'))

    return {
        'provider': provider,
        'api_key': api_key,
        'model': model,
        'base_url': base_url or None,
        'temperature': temperature,
    }


def _build_system_prompt(env, uid):
    """Build system prompt with available model information."""
    from .tool_registry import ToolRegistry, MODEL_MAPPING
    from .foggy_client import FoggyClient

    # Get model descriptions from Foggy metadata
    model_descriptions = []
    try:
        client = FoggyClient.from_config(env)
        # Use describe_model_internal for each accessible model
        accessible_models = set()
        user_env = env(user=uid)
        for odoo_model, qm_name in MODEL_MAPPING.items():
            try:
                if odoo_model in user_env and user_env['ir.model.access'].check(
                    odoo_model, 'read', raise_exception=False
                ):
                    accessible_models.add(qm_name)
            except Exception:
                pass

        for qm_name in sorted(accessible_models):
            model_descriptions.append(f"- **{qm_name}**")
    except Exception as e:
        _logger.warning("Failed to build model descriptions: %s", e)
        model_descriptions.append("(Model list unavailable — check Foggy MCP Server connection)")

    return SYSTEM_PROMPT_TEMPLATE.format(
        model_descriptions='\n'.join(model_descriptions)
    )


def _build_litellm_tools(env, uid):
    """Convert Foggy MCP tools to litellm/OpenAI function calling format."""
    from .tool_registry import ToolRegistry
    from .foggy_client import FoggyClient

    try:
        client = FoggyClient.from_config(env)
        registry = ToolRegistry(client, cache_ttl=300)
        foggy_tools = registry.get_tools_for_user(env, uid)
    except Exception as e:
        _logger.error("Failed to load tools for LLM: %s", e)
        return []

    tools = []
    for tool in foggy_tools:
        name = tool.get('name', '')
        # Only expose query-related tools to LLM
        if name not in ('dataset.query_model', 'dataset.get_metadata', 'dataset.describe_model_internal'):
            continue

        fn_def = {
            'type': 'function',
            'function': {
                'name': name.replace('.', '_'),  # OpenAI doesn't allow dots
                'description': tool.get('description', ''),
                'parameters': tool.get('inputSchema', {'type': 'object', 'properties': {}}),
            }
        }
        tools.append(fn_def)

    return tools


def _execute_tool_call(env, uid, tool_name, arguments):
    """Execute a tool call through Foggy MCP Server with permission injection."""
    from .foggy_client import FoggyClient
    from .permission_bridge import compute_permission_slices
    from .tool_registry import QM_TO_ODOO_MODEL

    # Restore original tool name (dots)
    original_name = tool_name.replace('_', '.', 2)

    # For query_model, inject permission slices
    if original_name == 'dataset.query_model' and 'payload' in arguments:
        model_name = arguments.get('model', '')
        odoo_model = QM_TO_ODOO_MODEL.get(model_name, '')
        if odoo_model:
            try:
                slices = compute_permission_slices(env, uid, model_name)
                if slices:
                    payload = arguments.get('payload', {})
                    if isinstance(payload, str):
                        payload = json.loads(payload)
                    existing = payload.get('slice', [])
                    payload['slice'] = existing + slices
                    arguments['payload'] = payload
            except Exception as e:
                _logger.error("Permission injection failed for chat: %s", e)
                return {'error': f'Permission check failed: {e}'}

    # Call Foggy
    try:
        client = FoggyClient.from_config(env)
        result = client.call_tools_call(original_name, arguments)
        return result
    except Exception as e:
        _logger.error("Tool call failed: %s — %s", original_name, e)
        return {'error': str(e)}


def chat(env, uid, session_id, user_message):
    """
    Main chat function — send user message, get AI response.

    Args:
        env: Odoo environment
        uid: User ID
        session_id: foggy.chat.session ID
        user_message: User's text message

    Returns:
        dict with 'content' (assistant reply) and 'error' (if any)
    """
    try:
        import litellm
    except ImportError:
        return {
            'content': '',
            'error': 'litellm package not installed. Run: pip install litellm',
        }

    config = _get_llm_config(env)
    if not config['api_key']:
        return {
            'content': '',
            'error': 'LLM API key not configured. Go to Settings → Foggy MCP → AI Chat.',
        }

    Session = env['foggy.chat.session'].sudo()
    Message = env['foggy.chat.message'].sudo()

    # Get or create session
    session = Session.browse(session_id) if session_id else None
    if not session or not session.exists():
        session = Session.create({
            'user_id': uid,
            'name': user_message[:50] + ('...' if len(user_message) > 50 else ''),
        })

    # Save user message
    Message.create({
        'session_id': session.id,
        'role': 'user',
        'content': user_message,
    })

    # Build conversation history
    system_prompt = _build_system_prompt(env, uid)
    messages = [{'role': 'system', 'content': system_prompt}]

    # Load last N messages from session (keep context manageable)
    history = Message.search([
        ('session_id', '=', session.id),
        ('role', 'in', ['user', 'assistant']),
    ], order='create_date asc, id asc', limit=20)

    for msg in history:
        messages.append({
            'role': msg.role,
            'content': msg.content or '',
        })

    # Build tools
    tools = _build_litellm_tools(env, uid)

    # Configure litellm
    litellm.api_key = config['api_key']
    if config['base_url']:
        litellm.api_base = config['base_url']

    model_name = config['model']
    # litellm provider prefix handling
    provider = config['provider']
    if provider == 'ollama' and not model_name.startswith('ollama/'):
        model_name = f'ollama/{model_name}'
    elif provider == 'deepseek' and not model_name.startswith('deepseek/'):
        model_name = f'deepseek/{model_name}'

    # LLM call with tool calling loop
    try:
        for round_idx in range(MAX_TOOL_ROUNDS):
            call_kwargs = {
                'model': model_name,
                'messages': messages,
                'temperature': config['temperature'],
            }
            if tools:
                call_kwargs['tools'] = tools
                call_kwargs['tool_choice'] = 'auto'

            if config['base_url']:
                call_kwargs['api_base'] = config['base_url']

            response = litellm.completion(**call_kwargs)
            choice = response.choices[0]
            assistant_msg = choice.message

            # Check for tool calls
            if assistant_msg.tool_calls:
                # Add assistant message with tool calls to history
                messages.append(assistant_msg.model_dump())

                for tool_call in assistant_msg.tool_calls:
                    fn_name = tool_call.function.name
                    try:
                        fn_args = json.loads(tool_call.function.arguments)
                    except json.JSONDecodeError:
                        fn_args = {}

                    _logger.info("Chat tool call: %s(%s)", fn_name, json.dumps(fn_args, ensure_ascii=False)[:200])

                    # Execute tool
                    result = _execute_tool_call(env, uid, fn_name, fn_args)
                    result_str = json.dumps(result, ensure_ascii=False, default=str)

                    # Add tool result to conversation
                    messages.append({
                        'role': 'tool',
                        'tool_call_id': tool_call.id,
                        'content': result_str[:8000],  # Truncate if too long
                    })

                # Continue loop for LLM to process tool results
                continue

            # No tool calls — we have the final response
            final_content = assistant_msg.content or ''

            # Save assistant response
            Message.create({
                'session_id': session.id,
                'role': 'assistant',
                'content': final_content,
            })

            return {
                'session_id': session.id,
                'content': final_content,
                'error': None,
            }

        # Max rounds exceeded
        return {
            'session_id': session.id,
            'content': 'I reached the maximum number of tool calls. Please try a simpler question.',
            'error': 'max_tool_rounds_exceeded',
        }

    except Exception as e:
        _logger.exception("LLM chat error")
        error_msg = str(e)
        # Common error messages for better UX
        if 'api_key' in error_msg.lower() or 'authentication' in error_msg.lower():
            error_msg = 'Invalid LLM API key. Please check Settings → Foggy MCP → AI Chat.'
        elif 'rate_limit' in error_msg.lower():
            error_msg = 'LLM rate limit reached. Please wait a moment and try again.'

        return {
            'session_id': session.id if session else None,
            'content': '',
            'error': error_msg,
        }
