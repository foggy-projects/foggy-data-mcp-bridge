# -*- coding: utf-8 -*-
import logging
import os
import platform
import secrets
import shlex

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
        res.update({
            'db_host': config.get('db_host') or 'localhost',
            'db_port': str(config.get('db_port') or '5432'),
            'db_user': config.get('db_user') or 'odoo',
            'db_password': config.get('db_password') or '',
            'db_name': self.env.cr.dbname,
            'auth_token': 'foggy_' + secrets.token_hex(16),
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

        # Build environment variables - no database config, uses DataSource API
        env_vars = [
            f'-e SPRING_PROFILES_ACTIVE=lite,odoo',
            f'-e FOGGY_AUTH_TOKEN={self.auth_token}',
        ]

        # Platform-specific options
        extra_opts = []
        if system == 'Linux':
            # Linux requires explicit host gateway
            extra_opts.append('--add-host=host.docker.internal:host-gateway')
        elif system == 'Windows':
            # Windows Docker Desktop handles this automatically
            pass
        elif system == 'Darwin':
            # macOS Docker Desktop handles this automatically
            pass

        cmd = (
            f"docker run -d \\\n"
            f"  --name foggy-mcp \\\n"
            f"  -p {self.foggy_port}:8080 \\\n"
            f"  {' '.join(env_vars)} \\\n"
        )

        if extra_opts:
            cmd += f"  {' '.join(extra_opts)} \\\n"

        cmd += (
            f"  --restart unless-stopped \\\n"
            f"  {FOGGY_DOCKER_IMAGE}"
        )

        return cmd

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

        # Handle localhost -> host.docker.internal for Docker
        db_host = self.db_host
        if db_host in ('localhost', '127.0.0.1'):
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