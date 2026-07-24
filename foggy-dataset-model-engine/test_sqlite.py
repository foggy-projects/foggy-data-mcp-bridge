import sqlite3
sql = """SELECT t0.salesDate$year, t0.salesDate$month, t0.unitPrice, t0.unitPrice__prior, (unitPrice - unitPrice__prior) AS unitPrice__diff, CASE WHEN unitPrice__prior = 0 THEN NULL ELSE (unitPrice - unitPrice__prior) / unitPrice__prior END AS unitPrice__ratio
FROM (
SELECT salesDate$year, salesDate$month, unitPrice, unitPrice__prior, (unitPrice - unitPrice__prior) AS unitPrice__diff, CASE WHEN unitPrice__prior = 0 THEN NULL ELSE (unitPrice - unitPrice__prior) / unitPrice__prior END AS unitPrice__ratio
FROM (SELECT *
FROM (SELECT salesDate$year, salesDate$month, unitPrice AS unitPrice
FROM (select d1.year "salesDate$year", d1.month "salesDate$month", SUM(t1.tax_amount+1) "taxAmount2", SUM(t1.unit_price) "unitPrice" from fact_sales t1 left join dim_date d1 on t1.date_key=d1.date_key group by d1.year, d1.month) AS cte_0) AS t0
LEFT JOIN (SELECT salesDate$month, unitPrice AS unitPrice__prior
FROM (select d1.year "salesDate$year", d1.month "salesDate$month", SUM(t1.tax_amount+1) "taxAmount2", SUM(t1.unit_price) "unitPrice" from fact_sales t1 left join dim_date d1 on t1.date_key=d1.date_key group by d1.year, d1.month) AS cte_0) AS t1 ON t0.salesDate$month = t1.salesDate$month) AS cte_3
) AS t0"""

try:
    con = sqlite3.connect(':memory:')
    con.execute("CREATE TABLE fact_sales (date_key INT, unit_price INT, tax_amount INT)")
    con.execute("CREATE TABLE dim_date (date_key INT, year INT, month INT)")
    print(con.execute(sql).fetchall())
    print("SUCCESS")
except Exception as e:
    print("ERROR:", e)
