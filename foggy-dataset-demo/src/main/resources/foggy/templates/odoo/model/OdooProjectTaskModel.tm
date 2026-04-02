/**
 * Odoo Project Task Model (project.task)
 *
 * @description Project tasks with project, stage, assignee, partner, and company dimensions.
 *              Requires 'project' Odoo module to be installed.
 */
import { dicts } from '../dicts.fsscript';
import { jsonbCaption } from '../odoo17.fsscript';

export const model = {
    name: 'OdooProjectTaskModel',
    caption: 'Project Tasks',
    tableName: 'project_task',
    dataSourceName: 'odoo',
    idColumn: 'id',

    dimensions: [
        {
            name: 'project',
            tableName: 'project_project',
            foreignKey: 'project_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Project',
            description: 'Parent project'
        },
        {
            name: 'stage',
            tableName: 'project_task_type',
            foreignKey: 'stage_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Stage',
            description: 'Kanban stage (e.g. To Do, In Progress, Done)'
        },
        {
            name: 'assignee',
            tableName: 'res_users',
            foreignKey: 'user_id',
            primaryKey: 'id',
            captionColumn: 'login',
            caption: 'Assignee',
            description: 'Primary assigned user'
        },
        {
            name: 'partner',
            tableName: 'res_partner',
            foreignKey: 'partner_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Customer',
            description: 'Related customer or contact',
            properties: [
                { column: 'email', caption: 'Email', type: 'STRING' },
                { column: 'phone', caption: 'Phone', type: 'STRING' },
                { column: 'is_company', caption: 'Is Company', type: 'BOOL' }
            ]
        },
        {
            name: 'company',
            tableName: 'res_company',
            foreignKey: 'company_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Company',
            description: 'Operating company',
            closureTableName: 'res_company_closure',
            parentKey: 'parent_id',
            childKey: 'company_id'
        },
        {
            name: 'parentTask',
            tableName: 'project_task',
            foreignKey: 'parent_id',
            primaryKey: 'id',
            captionDef: jsonbCaption(),
            caption: 'Parent Task',
            description: 'Parent task (sub-task hierarchy)'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Task Title', type: 'STRING', description: 'Task name (JSONB translatable)' },
        { column: 'state', caption: 'State', type: 'STRING', dictRef: dicts.project_task_state,
          description: 'Lifecycle state: in_progress, done, canceled, waiting' },
        { column: 'priority', caption: 'Priority', type: 'STRING', dictRef: dicts.project_task_priority },
        { column: 'kanban_state', caption: 'Kanban State', type: 'STRING',
          description: 'normal, done, or blocked' },
        { column: 'date_deadline', caption: 'Deadline', type: 'DAY' },
        { column: 'date_assign', caption: 'Assigning Date', type: 'DATETIME',
          description: 'Date when the task was first assigned' },
        { column: 'date_last_stage_update', caption: 'Last Stage Update', type: 'DATETIME' },
        { column: 'color', caption: 'Color Index', type: 'INTEGER' },
        { column: 'sequence', caption: 'Sequence', type: 'INTEGER' },
        { column: 'active', caption: 'Active', type: 'BOOL' },
        { column: 'description', caption: 'Description', type: 'STRING' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        { column: 'working_hours_open', caption: 'Working Hours to Assign', type: 'NUMBER', aggregation: 'avg',
          description: 'Average hours between creation and first assignment' },
        { column: 'working_hours_close', caption: 'Working Hours to Close', type: 'NUMBER', aggregation: 'avg',
          description: 'Average hours between creation and stage set to closing/folded stage' },
        { column: 'working_days_open', caption: 'Working Days to Assign', type: 'NUMBER', aggregation: 'avg' },
        { column: 'working_days_close', caption: 'Working Days to Close', type: 'NUMBER', aggregation: 'avg' },
        {
            column: 'id',
            name: 'taskCount',
            caption: 'Task Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
