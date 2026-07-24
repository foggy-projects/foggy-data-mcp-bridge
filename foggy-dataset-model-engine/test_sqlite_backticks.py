import sqlite3
sql = """SELECT salesDate$year, salesDate$month
FROM (SELECT *
FROM (SELECT salesDate$year, salesDate$month
FROM (select 2024 `salesDate$year`, 1 `salesDate$month` from fact_sales t1 left join dim_date d1 on t1.date_key=d1.date_key group by d1.year, d1.month) AS cte_0) AS t0
) AS cte_3"""

try:
    con = sqlite3.connect(':memory:')
    con.execute("CREATE TABLE fact_sales (date_key INT, unit_price INT, tax_amount INT, customer_key INT)")
    con.execute("CREATE TABLE dim_date (date_key INT, year INT, month INT)")
    print(con.execute(sql).fetchall())
    print("SUCCESS")
except Exception as e:
    print("ERROR:", e)
