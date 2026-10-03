/**
 * JSON-backed property used to verify formulaDef fields in query slices.
 */
export const model = {
    name: 'FactJsonFormulaFilterModel',
    caption: 'JSON 公式筛选测试',
    tableName: 'json_formula_filter_fixture',
    idColumn: 'id',

    properties: [
        {
            column: 'id',
            name: 'id',
            caption: '记录ID',
            type: 'STRING'
        },
        {
            column: 'json_payload',
            name: 'payload',
            caption: 'JSON载荷',
            type: 'STRING'
        },
        {
            column: 'json_payload',
            name: 'mappedCityCode',
            caption: '映射城市编码',
            type: 'STRING',
            formulaDef: {
                value: "json_extract(alias.json_payload, '$.cityCode')"
            },
            dialectFormulaDef: {
                mysql: {
                    value: "JSON_UNQUOTE(JSON_EXTRACT(alias.json_payload, '$.cityCode'))"
                },
                postgresql: {
                    value: "CAST(alias.json_payload AS jsonb) ->> 'cityCode'"
                },
                sqlserver: {
                    value: "JSON_VALUE(alias.json_payload, '$.cityCode')"
                }
            }
        }
    ]
};
