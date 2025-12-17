# ============================================
# Foggy Demo MySQL - 预置演示数据
# ============================================
FROM mysql:5.7

# 复制初始化脚本到容器
COPY init-scripts/ /docker-entrypoint-initdb.d/

# 复制自定义配置
COPY conf/my.cnf /etc/mysql/conf.d/custom.cnf

# 设置字符集
ENV LANG=C.UTF-8
