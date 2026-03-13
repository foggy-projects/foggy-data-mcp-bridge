package com.foggyframework.dataviewer.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 数据浏览器页面控制器
 * <p>
 * 提供SPA页面的路由支持
 */
@Controller
@RequestMapping("/data-viewer")
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ViewerPageController {

    /**
     * 主页面 - 重定向到具体查询页面
     */
    @GetMapping("")
    public String index() {
        return "redirect:/data-viewer/index.html";
    }

    /**
     * 查看特定查询的数据
     * <p>
     * 返回SPA的index.html，由前端路由处理
     */
    @GetMapping("/view/{model}/{queryId}")
    public String viewQuery(@PathVariable String model, @PathVariable String queryId) {
        return "forward:/data-viewer/index.html";
    }
}
