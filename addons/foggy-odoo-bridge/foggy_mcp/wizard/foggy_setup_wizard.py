# -*- coding: utf-8 -*-
import logging
import os

from odoo import api, fields, models, tools

_logger = logging.getLogger(__name__)

# JAR download URL pattern
JAR_DOWNLOAD_URL = (
    'https://github.com/nicholasgasior/foggy-data-mcp-bridge/releases/latest'
)

# All 8 Odoo query models
QUERY_MODELS = [
    'OdooSaleOrderQueryModel',
    'OdooSaleOrderLineQueryModel',
    'OdooPurchaseOrderQueryModel',
    'OdooAccountMoveQueryModel',
    'OdooStockPickingQueryModel',
    'OdooHrEmployeeQueryModel',
    'OdooResPartnerQueryModel',
    'OdooResCompanyQueryModel',
]


class FoggySetupWizard(models.TransientModel):
    _name = 'foggy.setup.wizard'
    _description = 'Foggy MCP Setup Wizard'

    # ── Step control ──────────────────────────────────────────────────

    state = fields.Selection([
        ('deploy_method', 'Deployment Method'),
        ('server_config', 'Server Configuration'),
        ('closure_tables', 'Closure Tables'),
        ('test_connection', 'Test Connection'),
        ('done', 'Done'),
    ], string='Step', default='deploy_method', required=True)

    # ── Step 1: Deploy method ─────────────────────────────────────────

    deploy_method = fields.Selection([
        ('docker', 'Docker (Recommended)'),
        ('manual', 'Manual JAR'),
    ], string='Deployment Method', default='docker')

    # ── Step 2: Server config ─────────────────────────────────────────

    db_host = fields.Char(string='Database Host')
    db_port = fields.Char(string='Database Port')
    db_user = fields.Char(string='Database User')
    db_password = fields.Char(string='Database Password')
    db_name = fields.Char(string='Database Name')
    foggy_port = fields.Integer(string='Foggy Port', default=7108)
    foggy_url = fields.Char(
        string='Foggy MCP URL',
        default='http://localhost:7108',
    )
    models_path = fields.Char(
        string='Models Path',
        help='Absolute path to the foggy-models directory',
    )
    docker_compose_content = fields.Text(
        string='Docker Compose',
        readonly=True,
    )
    manual_command = fields.Text(
        string='Start Command',
        readonly=True,
    )

    # ── Step 3: Closure tables ────────────────────────────────────────

    closure_status = fields.Text(
        string='Closure Table Status',
        readonly=True,
    )

    # ── Step 4: Connection test ───────────────────────────────────────

    connection_status = fields.Text(
        string='Connection Status',
        readonly=True,
    )

    # ══════════════════════════════════════════════════════════════════
    # Defaults
    # ══════════════════════════════════════════════════════════════════

    @api.model
    def default_get(self, fields_list):
        res = super().default_get(fields_list)
        # Auto-detect database connection from odoo.conf
        config = tools.config
        res.update({
            'db_host': config.get('db_host') or 'localhost',
            'db_port': str(config.get('db_port') or '5432'),
            'db_user': config.get('db_user') or 'odoo',
            'db_password': config.get('db_password') or '',
            'db_name': self.env.cr.dbname,
            'models_path': self._get_models_path(),
        })
        return res

    # ══════════════════════════════════════════════════════════════════
    # Navigation
    # ══════════════════════════════════════════════════════════════════

    _STEPS = [
        'deploy_method', 'server_config', 'closure_tables',
        'test_connection', 'done',
    ]

    def action_next(self):
        """Advance to next step."""
        self.ensure_one()
        idx = self._STEPS.index(self.state)
        if idx < len(self._STEPS) - 1:
            next_state = self._STEPS[idx + 1]
            vals = {'state': next_state}

            # Generate config on entering server_config step
            if next_state == 'server_config':
                vals['docker_compose_content'] = self._generate_docker_compose()
                vals['manual_command'] = self._generate_manual_command()

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
    # Step 2: Config generation
    # ══════════════════════════════════════════════════════════════════

    def _get_models_path(self):
        """Return absolute path to the bundled foggy-models directory."""
        return os.path.join(
            os.path.dirname(os.path.dirname(__file__)),
            'setup', 'foggy-models',
        )

    def _generate_docker_compose(self):
        """Render docker-compose.yml from template with DB parameters."""
        self.ensure_one()
        template_path = os.path.join(
            os.path.dirname(os.path.dirname(__file__)),
            'setup', 'docker-compose.yml.template',
        )
        try:
            with open(template_path, 'r', encoding='utf-8') as f:
                template = f.read()
        except FileNotFoundError:
            return '# ERROR: Template file not found at %s' % template_path

        # If Odoo connects to DB via localhost, Docker needs host.docker.internal
        docker_db_host = self.db_host
        if docker_db_host in ('localhost', '127.0.0.1'):
            docker_db_host = 'host.docker.internal'

        return template.format(
            db_host=docker_db_host,
            db_port=self.db_port,
            db_user=self.db_user,
            db_password=self.db_password,
            db_name=self.db_name,
            foggy_port=self.foggy_port or 7108,
            models_path=self.models_path or self._get_models_path(),
        )

    def _generate_manual_command(self):
        """Generate java -jar startup command."""
        self.ensure_one()
        models_path = self.models_path or self._get_models_path()
        model_args = '\n'.join(
            f'  --foggy.mcp.semantic.model-list[{i}]={m}'
            for i, m in enumerate(QUERY_MODELS)
        )
        return (
            f'java -jar foggy-mcp-launcher-*.jar \\\n'
            f'  --spring.profiles.active=lite \\\n'
            f'  --spring.datasource.url='
            f'jdbc:postgresql://{self.db_host}:{self.db_port}/{self.db_name}'
            f' \\\n'
            f'  --spring.datasource.username={self.db_user} \\\n'
            f'  --spring.datasource.password={self.db_password} \\\n'
            f'  --spring.datasource.driver-class-name='
            f'org.postgresql.Driver \\\n'
            f'  --foggy.bundle.external.enabled=true \\\n'
            f'  --foggy.bundle.external.bundles[0].name=odoo-models \\\n'
            f'  "--foggy.bundle.external.bundles[0].path={models_path}"'
            f' \\\n'
            f'  --foggy.bundle.external.bundles[0].namespace=odoo \\\n'
            f'  --foggy.demo.enabled=false \\\n'
            f'{model_args}'
        )

    def action_regenerate_config(self):
        """Re-generate config after user edits DB parameters."""
        self.ensure_one()
        self.write({
            'docker_compose_content': self._generate_docker_compose(),
            'manual_command': self._generate_manual_command(),
        })
        return self._reopen()

    # ══════════════════════════════════════════════════════════════════
    # Step 3: Closure tables
    # ══════════════════════════════════════════════════════════════════

    def action_init_closure_tables(self):
        """Execute closure table SQL on the Odoo PostgreSQL database."""
        self.ensure_one()
        sql_path = os.path.join(
            os.path.dirname(os.path.dirname(__file__)),
            'setup', 'sql', 'refresh_closure_tables.sql',
        )
        try:
            with open(sql_path, 'r', encoding='utf-8') as f:
                sql = f.read()
            # Create tables and functions
            self.env.cr.execute(sql)
            # Populate closure tables
            self.env.cr.execute("SELECT refresh_all_closures()")
            self.env.cr.fetchone()

            # Verify row counts
            tables = [
                'res_company_closure',
                'hr_department_closure',
                'hr_employee_closure',
                'res_partner_closure',
            ]
            counts = []
            for table in tables:
                self.env.cr.execute(
                    "SELECT count(*) FROM %s" % table  # noqa: S608
                )
                count = self.env.cr.fetchone()[0]
                counts.append(f"  {table}: {count} rows")

            self.write({
                'closure_status': (
                    "Closure tables initialized successfully!\n\n"
                    + '\n'.join(counts)
                ),
            })
            _logger.info("Closure tables initialized via setup wizard")
        except Exception as e:
            self.write({
                'closure_status': f"Error: {e}",
            })
            _logger.exception("Failed to initialize closure tables")

        return self._reopen()

    # ══════════════════════════════════════════════════════════════════
    # Step 4: Connection test
    # ══════════════════════════════════════════════════════════════════

    def action_test_connection(self):
        """Test connectivity to Foggy MCP Server."""
        self.ensure_one()
        url = self.foggy_url or 'http://localhost:7108'

        try:
            import requests  # noqa: E401  (delayed import — not an Odoo dep)
            r = requests.get(
                f'{url.rstrip("/")}/actuator/health',
                timeout=5,
            )
            if r.status_code == 200:
                # Save configuration
                ICP = self.env['ir.config_parameter'].sudo()
                ICP.set_param('foggy_mcp.server_url', url)

                self.write({
                    'connection_status': (
                        f"Connected to Foggy MCP Server at {url}\n"
                        f"Response: {r.text[:200]}\n\n"
                        "Server URL has been saved to Odoo configuration."
                    ),
                })
            else:
                self.write({
                    'connection_status': (
                        f"Server responded with HTTP {r.status_code}\n"
                        f"Response: {r.text[:200]}"
                    ),
                })
        except ImportError:
            self.write({
                'connection_status': (
                    "Error: 'requests' library not available.\n"
                    "Install it with: pip install requests"
                ),
            })
        except Exception as e:
            self.write({
                'connection_status': f"Cannot reach {url}\nError: {e}",
            })

        return self._reopen()

    def action_finish(self):
        """Close the wizard and redirect to API Key creation."""
        return {
            'type': 'ir.actions.act_window',
            'res_model': 'foggy.api.key',
            'view_mode': 'form',
            'target': 'current',
            'context': {'default_name': 'My First API Key'},
        }
