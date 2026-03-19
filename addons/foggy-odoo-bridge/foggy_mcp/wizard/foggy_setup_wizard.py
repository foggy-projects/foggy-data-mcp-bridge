# -*- coding: utf-8 -*-
import logging
import os
import platform
import secrets
import shlex
import subprocess
import json

from odoo import api, fields, models, tools

_logger = logging.getLogger(__name__)

# Docker image with built-in Odoo models
FOGGY_DOCKER_IMAGE = 'foggysource/foggy-odoo-mcp:v8.1.8-beta'

# All Odoo query models (built into Docker image)
QUERY_MODELS = [
    'OdooSaleOrderQueryModel',
    'OdooSaleOrderLineQueryModel',
    'OdooPurchaseOrderQueryModel',
    'OdooAccountMoveQueryModel',
    'OdooStockPickingQueryModel',
    'OdooHrEmployeeQueryModel',
    'OdooResPartnerQueryModel',
    'OdooResCompanyQueryModel',
    'OdooCrmLeadQueryModel',
]


def _get_docker_network_info():
    """
    Detect if running in Docker and get network information.

    Returns:
        dict: {
            'in_docker': bool,
            'container_id': str or None,
            'network_name': str or None,
            'network_mode': str or None,  # 'bridge', 'host', 'none', or custom network name
            'error': str or None
        }
    """
    result = {
        'in_docker': False,
        'container_id': None,
        'network_name': None,
        'network_mode': None,
        'error': None,
    }

    try:
        # Check if we're inside a Docker container
        # Method 1: Check /.dockerenv file
        if os.path.exists('/.dockerenv'):
            result['in_docker'] = True

        # Method 2: Check /proc/1/cgroup for docker signature (cgroup v1)
        if not result['in_docker']:
            try:
                with open('/proc/1/cgroup', 'r') as f:
                    cgroup = f.read()
                    if 'docker' in cgroup or 'containerd' in cgroup:
                        result['in_docker'] = True
            except Exception:
                pass

        # Method 3: Check for container environment variable
        if not result['in_docker']:
            # Many container runtimes set this
            if os.environ.get('KUBERNETES_SERVICE_HOST') or os.environ.get('container'):
                result['in_docker'] = True

        if not result['in_docker']:
            return result

        # Get container ID from hostname (usually container ID in Docker)
        try:
            result['container_id'] = os.uname().nodename[:12]
        except Exception:
            pass

        # Try to get network info via Docker socket
        docker_socket = '/var/run/docker.sock'
        if os.path.exists(docker_socket):
            try:
                # Use curl to query Docker API (lighter than installing docker SDK)
                cmd = [
                    'curl', '--silent', '--unix-socket', docker_socket,
                    f'http://localhost/containers/{result["container_id"]}/json'
                ]
                proc = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
                if proc.returncode == 0 and proc.stdout:
                    container_info = json.loads(proc.stdout)

                    # Get network settings
                    network_settings = container_info.get('NetworkSettings', {})
                    networks = network_settings.get('Networks', {})

                    if networks:
                        # Get the first network (usually the main one)
                        for net_name, net_config in networks.items():
                            if net_name not in ('bridge', 'host', 'none'):
                                # Custom network (like foggy-odoo_default)
                                result['network_name'] = net_name
                                result['network_mode'] = 'custom'
                                break
                            else:
                                result['network_name'] = net_name
                                result['network_mode'] = net_name
                                break

                    # Also check HostConfig.NetworkMode
                    host_config = container_info.get('HostConfig', {})
                    network_mode = host_config.get('NetworkMode', '')
                    if network_mode and network_mode not in ('default', ''):
                        result['network_mode'] = network_mode
                        if network_mode not in ('bridge', 'host', 'none'):
                            result['network_name'] = network_mode

            except Exception as e:
                result['error'] = f"Docker API error: {e}"
        else:
            # Docker socket not mounted - try alternative methods
            result['error'] = "Docker socket not mounted"

            # Method 1: Check if db_host is a Docker service name (not IP/localhost)
            # This is the most reliable indicator of Docker networking
            try:
                db_host = tools.config.get('db_host', '')
                if db_host and db_host not in ('localhost', '127.0.0.1', 'False', False, ''):
                    # db_host is a hostname like 'postgres' - definitely Docker network
                    # We can't know the exact network name without Docker API,
                    # but we know we're in a Docker network
                    result['network_name'] = 'docker_network'  # Placeholder
                    result['network_mode'] = 'custom'
                    result['error'] = None  # Clear error
                    _logger.info("Detected Docker network via db_host: %s", db_host)
            except Exception as e:
                _logger.warning("Failed to detect network via db_host: %s", e)

    except Exception as e:
        result['error'] = str(e)

    _logger.info("Docker network detection: %s", result)
    return result


class FoggySetupWizard(models.TransientModel):
    _name = 'foggy.setup.wizard'
    _description = 'Foggy MCP Setup Wizard'

    # ── Step control ──────────────────────────────────────────────────

    state = fields.Selection([
        ('welcome', 'Welcome'),
        ('deploy', 'Deploy'),
        ('connection', 'Connection'),
        ('datasource', 'Data Source'),
        ('closure', 'Closure Tables'),
        ('done', 'Done'),
    ], string='Step', default='welcome', required=True)

    # ── Step 2: Deploy config ─────────────────────────────────────────

    foggy_port = fields.Integer(string='Foggy MCP Port', default=7108)
    auth_token = fields.Char(string='Auth Token', readonly=True)

    # Docker network detection (readonly, for display)
    docker_network_name = fields.Char(string='Docker Network', readonly=True)
    docker_network_mode = fields.Char(string='Network Mode', readonly=True)
    docker_network_detected = fields.Boolean(string='Docker Network Detected', readonly=True)
    docker_socket_available = fields.Boolean(string='Docker Socket Available', readonly=True)

    deploy_command = fields.Text(string='Deploy Command', readonly=True)
    deploy_status = fields.Text(string='Status', readonly=True)

    # ── Step 3: Connection test ───────────────────────────────────────

    foggy_url = fields.Char(string='Foggy MCP URL', default='http://localhost:7108')
    connection_status = fields.Text(string='Connection Status', readonly=True)

    # ── Step 4: Data Source ───────────────────────────────────────────

    db_host = fields.Char(string='Database Host')
    db_port = fields.Char(string='Database Port')
    db_user = fields.Char(string='Database User')
    db_password = fields.Char(string='Database Password')
    db_name = fields.Char(string='Database Name')
    datasource_status = fields.Text(string='Data Source Status', readonly=True)

    # ── Step 5: Closure tables ────────────────────────────────────────

    closure_status = fields.Text(string='Closure Table Status', readonly=True)

    # ══════════════════════════════════════════════════════════════════
    # Defaults
    # ══════════════════════════════════════════════════════════════════

    @api.model
    def default_get(self, fields_list):
        res = super().default_get(fields_list)
        config = tools.config

        # Detect Docker environment
        docker_info = _get_docker_network_info()

        # Set smart default URL based on Docker environment
        if docker_info['in_docker'] and docker_info['network_name']:
            # Same Docker network - use container name
            default_url = 'http://foggy-mcp:8080'
        elif docker_info['in_docker']:
            # In Docker but no custom network
            default_url = 'http://host.docker.internal:8080'
        else:
            # Not in Docker - use localhost
            default_url = 'http://localhost:8080'

        # Determine if we have real network info (from Docker socket)
        # or just inferred from db_host
        socket_available = os.path.exists('/var/run/docker.sock')
        real_network_name = docker_info.get('network_name') if socket_available else None

        res.update({
            'db_host': config.get('db_host') or 'localhost',
            'db_port': str(config.get('db_port') or '5432'),
            'db_user': config.get('db_user') or 'odoo',
            'db_password': config.get('db_password') or '',
            'db_name': self.env.cr.dbname,
            'auth_token': 'foggy_' + secrets.token_hex(16),
            'foggy_url': default_url,
            'docker_network_name': real_network_name,
            'docker_network_mode': docker_info.get('network_mode'),
            'docker_network_detected': docker_info['in_docker'] and bool(docker_info.get('network_name')),
            'docker_socket_available': socket_available,
        })
        return res

    # ══════════════════════════════════════════════════════════════════
    # Navigation
    # ══════════════════════════════════════════════════════════════════

    _STEPS = ['welcome', 'deploy', 'connection', 'datasource', 'closure', 'done']

    def action_next(self):
        """Advance to next step."""
        self.ensure_one()
        idx = self._STEPS.index(self.state)
        if idx < len(self._STEPS) - 1:
            next_state = self._STEPS[idx + 1]
            vals = {'state': next_state}

            if next_state == 'deploy':
                vals['deploy_command'] = self._generate_deploy_command()

            self.write(vals)
        return self._reopen()

    def action_prev(self):
        """Go back to previous step."""
        self.ensure_one()
        idx = self._STEPS.index(self.state)
        if idx > 0:
            self.write({'state': self._STEPS[idx - 1]})
        return self._reopen()

    def _reopen(self):
        """Reopen the wizard on the same record."""
        return {
            'type': 'ir.actions.act_window',
            'res_model': self._name,
            'res_id': self.id,
            'view_mode': 'form',
            'target': 'new',
        }

    # ══════════════════════════════════════════════════════════════════
    # Deploy command generation
    # ══════════════════════════════════════════════════════════════════

    def _generate_deploy_command(self):
        """Generate one-line docker run command for current platform."""
        self.ensure_one()

        # Detect OS for cross-platform support
        system = platform.system()

        # Detect Docker environment and network
        docker_info = _get_docker_network_info()

        # Build environment variables - no database config, uses DataSource API
        env_vars = [
            f'-e SPRING_PROFILES_ACTIVE=lite,odoo',
            f'-e FOGGY_AUTH_TOKEN={self.auth_token}',
        ]

        # Network options
        network_opts = []
        port_mapping = f"-p {self.foggy_port}:8080"
        network_comment = ""

        if docker_info['in_docker'] and docker_info['network_name']:
            # We have network info
            if os.path.exists('/var/run/docker.sock'):
                # Real network name from Docker API
                network_opts.append(f"--network {docker_info['network_name']}")
            else:
                # Inferred network - user needs to replace placeholder
                network_opts.append("--network <NETWORK_NAME>")
                network_comment = "\n# ⚠️ Replace <NETWORK_NAME> with your Docker network name."
                network_comment += "\n#    Run: docker network ls"
                network_comment += f"\n#    Or join Odoo's network: --network container:{docker_info['container_id']}"
            _logger.info("Detected Docker network: %s", docker_info['network_name'])
        elif docker_info['in_docker'] and docker_info['network_mode'] == 'host':
            # Host network mode - no port mapping needed, uses host's network directly
            network_opts.append("--network host")
            port_mapping = ""  # Not needed in host mode
        elif system == 'Linux':
            # Linux requires explicit host gateway
            network_opts.append('--add-host=host.docker.internal:host-gateway')

        cmd = (
            f"docker run -d \\\n"
            f"  --name foggy-mcp \\\n"
        )

        if port_mapping:
            cmd += f"  {port_mapping} \\\n"

        cmd += f"  {' '.join(env_vars)} \\\n"

        if network_opts:
            cmd += f"  {' '.join(network_opts)} \\\n"

        cmd += (
            f"  --restart unless-stopped \\\n"
            f"  {FOGGY_DOCKER_IMAGE}"
        )

        if network_comment:
            cmd += network_comment

        return cmd

    def _get_foggy_url_hint(self):
        """Get hint for Foggy MCP URL based on Docker environment."""
        docker_info = _get_docker_network_info()

        if docker_info['in_docker'] and docker_info['network_name']:
            # Same Docker network - use container name
            return "http://foggy-mcp:8080"
        elif docker_info['in_docker']:
            # In Docker but no custom network - use host.docker.internal
            return "http://host.docker.internal:8080"
        else:
            # Not in Docker - use localhost
            return "http://localhost:8080"

    def action_regenerate_command(self):
        """Regenerate deploy command after user changes config."""
        self.ensure_one()
        self.write({'deploy_command': self._generate_deploy_command()})
        return self._reopen()

    # ══════════════════════════════════════════════════════════════════
    # Step 3: Connection test
    # ══════════════════════════════════════════════════════════════════

    def action_test_connection(self):
        """Test connectivity to Foggy MCP Server."""
        self.ensure_one()
        url = self.foggy_url or 'http://localhost:7108'

        try:
            import requests
            r = requests.get(f'{url.rstrip("/")}/actuator/health', timeout=5)
            if r.status_code == 200:
                # Save configuration
                ICP = self.env['ir.config_parameter'].sudo()
                ICP.set_param('foggy_mcp.server_url', url)
                ICP.set_param('foggy_mcp.auth_token', self.auth_token)

                self.write({
                    'connection_status': f"✅ Connected to Foggy MCP Server\n\n"
                                        f"URL: {url}\n"
                                        f"Auth Token: {self.auth_token}\n\n"
                                        f"Response: {r.text[:200]}"
                })
            else:
                self.write({
                    'connection_status': f"❌ Server responded with HTTP {r.status_code}\n\n{r.text[:200]}"
                })
        except ImportError:
            self.write({
                'connection_status': "❌ Error: 'requests' library not available.\n\nInstall: pip install requests"
            })
        except Exception as e:
            self.write({
                'connection_status': f"❌ Cannot reach {url}\n\nError: {e}"
            })

        return self._reopen()

    # ══════════════════════════════════════════════════════════════════
    # Step 4: Data Source Configuration
    # ══════════════════════════════════════════════════════════════════

    def action_configure_datasource(self):
        """Register Odoo database as data source in Foggy MCP Server."""
        self.ensure_one()

        url = self.foggy_url or 'http://localhost:7108'
        ICP = self.env['ir.config_parameter'].sudo()
        auth_token = ICP.get_param('foggy_mcp.auth_token', '')

        # Detect Docker environment
        docker_info = _get_docker_network_info()

        # Determine database host for Foggy MCP
        db_host = self.db_host
        if docker_info['in_docker'] and docker_info['network_name']:
            # Both Odoo and Foggy MCP are in the same Docker network
            # Use the PostgreSQL container name directly (usually 'postgres' or from Odoo config)
            # Check if db_host is localhost/127.0.0.1 -> use the postgres container name
            if db_host in ('localhost', '127.0.0.1'):
                # Try to get actual postgres container name from Odoo config or environment
                # Common patterns: 'postgres', 'db', 'odoo-postgres', etc.
                config = tools.config
                pg_host = config.get('db_host')
                if pg_host and pg_host not in ('localhost', '127.0.0.1', 'False', False):
                    db_host = pg_host
                else:
                    # Default to 'postgres' which is common in docker-compose setups
                    db_host = 'postgres'
        elif db_host in ('localhost', '127.0.0.1'):
            # Not in Docker or different network -> use host.docker.internal
            db_host = 'host.docker.internal'

        try:
            import requests

            # Call DataSource API
            r = requests.post(
                f'{url.rstrip("/")}/api/v1/datasource',
                headers={
                    'Content-Type': 'application/json',
                    'Authorization': f'Bearer {auth_token}',
                },
                json={
                    'name': 'odoo',
                    'host': db_host,
                    'port': int(self.db_port),
                    'database': self.db_name,
                    'username': self.db_user,
                    'password': self.db_password,
                    'driver': 'postgresql',
                },
                timeout=10,
            )

            if r.status_code == 200:
                # Test the connection
                r_test = requests.get(
                    f'{url.rstrip("/")}/api/v1/datasource/odoo/test',
                    headers={'Authorization': f'Bearer {auth_token}'},
                    timeout=10,
                )

                if r_test.status_code == 200:
                    result = r_test.json()
                    if result.get('data', {}).get('success'):
                        self.write({
                            'datasource_status': f"✅ Data source configured successfully!\n\n"
                                                f"Name: odoo\n"
                                                f"Host: {db_host}:{self.db_port}\n"
                                                f"Database: {self.db_name}"
                        })
                    else:
                        self.write({
                            'datasource_status': f"⚠️ Data source registered but connection test failed.\n\n"
                                                f"Error: {result.get('data', {}).get('message', 'Unknown error')}"
                        })
                else:
                    self.write({
                        'datasource_status': f"⚠️ Data source registered but test failed.\n\nHTTP {r_test.status_code}"
                    })
            else:
                self.write({
                    'datasource_status': f"❌ Failed to configure data source.\n\nHTTP {r.status_code}\n{r.text[:200]}"
                })

        except ImportError:
            self.write({
                'datasource_status': "❌ Error: 'requests' library not available.\n\nInstall: pip install requests"
            })
        except Exception as e:
            self.write({
                'datasource_status': f"❌ Error: {e}"
            })

        return self._reopen()

    # ══════════════════════════════════════════════════════════════════
    # Step 5: Closure tables
    # ══════════════════════════════════════════════════════════════════

    def action_init_closure_tables(self):
        """Execute closure table SQL."""
        self.ensure_one()
        sql_path = os.path.join(
            os.path.dirname(os.path.dirname(__file__)),
            'setup', 'sql', 'refresh_closure_tables.sql',
        )
        try:
            with open(sql_path, 'r', encoding='utf-8') as f:
                sql = f.read()
            self.env.cr.execute(sql)
            self.env.cr.execute("SELECT refresh_all_closures()")
            self.env.cr.fetchone()

            tables = ['res_company_closure', 'hr_department_closure',
                      'hr_employee_closure', 'res_partner_closure']
            counts = []
            for table in tables:
                self.env.cr.execute(f"SELECT count(*) FROM {table}")
                count = self.env.cr.fetchone()[0]
                counts.append(f"  {table}: {count} rows")

            self.write({
                'closure_status': "✅ Closure tables initialized!\n\n" + '\n'.join(counts)
            })
        except Exception as e:
            self.write({'closure_status': f"❌ Error: {e}"})

        return self._reopen()

    def action_skip_closure(self):
        """Skip closure table initialization."""
        self.ensure_one()
        return self.action_next()

    # ══════════════════════════════════════════════════════════════════
    # Step 6: Finish
    # ══════════════════════════════════════════════════════════════════

    def action_finish(self):
        """Close wizard and redirect to API Key creation."""
        return {
            'type': 'ir.actions.act_window',
            'res_model': 'foggy.api.key',
            'view_mode': 'form',
            'target': 'current',
            'context': {'default_name': 'My API Key'},
        }