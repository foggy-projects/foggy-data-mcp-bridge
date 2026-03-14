/**
 * Odoo HR Employee Model (hr.employee)
 *
 * @description Employee records with department, job, and company dimensions.
 *              Sensitive fields (e.g. salary) excluded — use forced filters for access control.
 */
export const model = {
    name: 'OdooHrEmployeeModel',
    caption: 'Employees',
    tableName: 'hr_employee',
    idColumn: 'id',

    dimensions: [
        {
            name: 'department',
            tableName: 'hr_department',
            foreignKey: 'department_id',
            primaryKey: 'id',
            captionColumn: 'complete_name',
            caption: 'Department',
            description: 'Employee department',
            closureTableName: 'hr_department_closure',
            parentKey: 'parent_id',
            childKey: 'department_id'
        },
        {
            name: 'job',
            tableName: 'hr_job',
            foreignKey: 'job_id',
            primaryKey: 'id',
            // Odoo 17: hr_job.name is JSONB (translatable field)
            // captionDef.column provides the column name; dialectFormulaDef extracts en_US
            captionDef: {
                column: 'name',
                dialectFormulaDef: {
                    postgresql: {
                        builder: (alias) => { return `${alias}.name ->> 'en_US'`; }
                    }
                }
            },
            caption: 'Job Position',
            description: 'Job position'
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
            name: 'parent',
            tableName: 'hr_employee',
            foreignKey: 'parent_id',
            primaryKey: 'id',
            captionColumn: 'name',
            caption: 'Manager',
            description: 'Direct manager',
            closureTableName: 'hr_employee_closure',
            parentKey: 'parent_id',
            childKey: 'employee_id'
        },
        {
            name: 'workLocation',
            tableName: 'hr_work_location',
            foreignKey: 'work_location_id',
            primaryKey: 'id',
            // hr_work_location.name is VARCHAR (not JSONB) in Odoo 17
            captionColumn: 'name',
            caption: 'Work Location',
            description: 'Work location'
        },
        {
            name: 'user',
            tableName: 'res_users',
            foreignKey: 'user_id',
            primaryKey: 'id',
            captionColumn: 'login',
            caption: 'Related User',
            description: 'Related system user'
        }
    ],

    properties: [
        { column: 'id', caption: 'ID', type: 'INTEGER' },
        { column: 'name', caption: 'Employee Name', type: 'STRING' },
        { column: 'job_title', caption: 'Job Title', type: 'STRING' },
        { column: 'work_email', caption: 'Work Email', type: 'STRING' },
        { column: 'work_phone', caption: 'Work Phone', type: 'STRING' },
        { column: 'mobile_phone', caption: 'Mobile', type: 'STRING' },
        { column: 'active', caption: 'Active', type: 'BOOL' },
        { column: 'gender', caption: 'Gender', type: 'STRING' },
        { column: 'marital', caption: 'Marital Status', type: 'STRING' },
        { column: 'employee_type', caption: 'Employee Type', type: 'STRING' },
        { column: 'create_date', caption: 'Created On', type: 'DATETIME' },
        { column: 'write_date', caption: 'Last Updated', type: 'DATETIME' }
    ],

    measures: [
        {
            column: 'id',
            name: 'employeeCount',
            caption: 'Employee Count',
            type: 'INTEGER',
            aggregation: 'COUNT_DISTINCT'
        }
    ]
};
