package rs.mds.inventory.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.mds.inventory.service.InventoryService;

import java.util.Map;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<Map<String, Integer>> getInventory() {
        return ResponseEntity.ok(inventoryService.getInventorySnapshot());
    }
}