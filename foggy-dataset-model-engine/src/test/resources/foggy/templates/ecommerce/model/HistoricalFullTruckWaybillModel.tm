/** Direct regression fixture for grouped totalData AVG semantics. */
export const model = {
    name: 'HistoricalFullTruckWaybillModel',
    caption: '历史整车运单',
    tableName: 'historical_full_truck_waybill',
    idColumn: 'waybill_id',

    dimensions: [
        {
            name: 'openingTime',
            tableName: 'dim_historical_waybill_date',
            foreignKey: 'opening_date_key',
            primaryKey: 'date_key',
            captionColumn: 'full_date',
            caption: '开单时间',
            timeRole: 'business_date',
            properties: [
                { column: 'year', caption: '年', type: 'INTEGER' }
            ]
        }
    ],

    measures: [
        {
            column: 'waybill_id',
            name: 'waybillCount',
            caption: '运单数',
            type: 'LONG',
            aggregation: 'COUNT'
        },
        {
            column: 'receivable_transport_amount',
            name: 'receivableTransportAmount',
            caption: '应收运输金额',
            type: 'MONEY',
            aggregation: 'SUM'
        },
        {
            column: 'receivable_transport_amount',
            name: 'averageTransportAmountPerWaybill',
            caption: '票均运输金额',
            type: 'MONEY',
            aggregation: 'AVG'
        }
    ]
};
