/**
 * 地理位置站点模型（用于测试 MongoDB 数组元素访问）
 *
 * @description 验证 location.coordinates.0 / .1 等数组索引字段在 $project 中的正确投影
 */
import '@mcpMongoTemplate';

export const model = {
    name: 'GeoStationModel',
    caption: '地理站点',
    tableName: 'geo_station_test',
    idColumn: '_id',
    type: 'mongo',
    mongoTemplate: mcpMongoTemplate,

    properties: [
        {
            column: '_id',
            name: 'id',
            caption: '站点ID',
            type: 'STRING'
        },
        {
            column: 'name',
            caption: '站点名称',
            type: 'STRING'
        },
        {
            column: 'location.type',
            name: 'locationType',
            caption: '位置类型',
            type: 'STRING'
        },
        {
            column: 'location.coordinates',
            name: 'coordinates',
            caption: '经纬度'
        },
        {
            column: 'location.coordinates.0',
            name: 'lng',
            caption: '经度',
            type: 'NUMBER'
        },
        {
            column: 'location.coordinates.1',
            name: 'lat',
            caption: '纬度',
            type: 'NUMBER'
        },
        {
            column: 'city',
            caption: '城市',
            type: 'STRING'
        },
        {
            column: 'status',
            caption: '状态',
            type: 'STRING'
        }
    ],

    measures: []
};
