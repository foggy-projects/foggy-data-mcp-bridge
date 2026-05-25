package com.foggyframework.dataviewer.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataviewer.domain.ListPresetDef;
import com.foggyframework.dataviewer.service.ListPresetService;
import com.foggyframework.dataviewer.service.ListPresetService.SaveListPresetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自定义列表 API 控制器。
 */
@RestController
@RequestMapping("/data-viewer/api/list-preset")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ListPresetController {

    private final ListPresetService listPresetService;

    @GetMapping("/users/{userId}/models/{model}")
    public RX<List<ListPresetDef>> list(
            @PathVariable String userId,
            @PathVariable String model,
            @RequestParam(value = "businessKey", required = false) String businessKey) {
        try {
            return RX.ok(listPresetService.list(userId, model, businessKey));
        } catch (IllegalArgumentException e) {
            return RX.failB(e.getMessage(), null);
        }
    }

    @GetMapping("/users/{userId}/models/{model}/default")
    public RX<ListPresetDef> getDefault(
            @PathVariable String userId,
            @PathVariable String model,
            @RequestParam(value = "businessKey", required = false) String businessKey) {
        try {
            return listPresetService.getDefault(userId, model, businessKey)
                    .map(RX::ok)
                    .orElse(RX.ok(null));
        } catch (IllegalArgumentException e) {
            return RX.failB(e.getMessage(), null);
        }
    }

    @PostMapping("/users/{userId}/models/{model}")
    public RX<ListPresetDef> create(
            @PathVariable String userId,
            @PathVariable String model,
            @RequestParam(value = "businessKey", required = false) String businessKey,
            @RequestBody SaveListPresetRequest request) {
        try {
            return RX.ok(listPresetService.create(userId, model, businessKey, request));
        } catch (IllegalArgumentException e) {
            return RX.failB(e.getMessage(), null);
        }
    }

    @GetMapping("/users/{userId}/presets/{id}")
    public RX<ListPresetDef> get(@PathVariable String userId, @PathVariable String id) {
        try {
            return listPresetService.get(userId, id)
                    .map(RX::ok)
                    .orElse(RX.notFound().build());
        } catch (IllegalArgumentException e) {
            return RX.failB(e.getMessage(), null);
        }
    }

    @PutMapping("/users/{userId}/presets/{id}")
    public RX<ListPresetDef> update(
            @PathVariable String userId,
            @PathVariable String id,
            @RequestBody SaveListPresetRequest request) {
        try {
            return listPresetService.update(userId, id, request)
                    .map(RX::ok)
                    .orElse(RX.notFound().build());
        } catch (IllegalArgumentException e) {
            return RX.failB(e.getMessage(), null);
        }
    }

    @DeleteMapping("/users/{userId}/presets/{id}")
    public RX<Void> delete(@PathVariable String userId, @PathVariable String id) {
        try {
            if (listPresetService.delete(userId, id)) {
                return RX.ok(null);
            }
            return RX.notFound().build();
        } catch (IllegalArgumentException e) {
            return RX.failB(e.getMessage(), null);
        }
    }

    @PostMapping("/users/{userId}/presets/{id}/default")
    public RX<ListPresetDef> setDefault(@PathVariable String userId, @PathVariable String id) {
        try {
            return listPresetService.setDefault(userId, id)
                    .map(RX::ok)
                    .orElse(RX.notFound().build());
        } catch (IllegalArgumentException e) {
            return RX.failB(e.getMessage(), null);
        }
    }

    @DeleteMapping("/users/{userId}/models/{model}/default")
    public RX<Void> clearDefault(
            @PathVariable String userId,
            @PathVariable String model,
            @RequestParam(value = "businessKey", required = false) String businessKey) {
        try {
            listPresetService.clearDefault(userId, model, businessKey);
            return RX.ok(null);
        } catch (IllegalArgumentException e) {
            return RX.failB(e.getMessage(), null);
        }
    }
}
