package com.shop.controller;

import com.shop.model.Owner;
import com.shop.model.Package;
import com.shop.service.OwnerService;
import com.shop.service.PackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/** JSON API used by the public packages page (admin edits are saved in the DB). */
@RestController
@RequestMapping("/api/packages")
public class PackageApiController {

    @Autowired private PackageService packageService;
    @Autowired private OwnerService ownerService;

    private Map<String, Object> toMap(Package p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("price", p.getPrice());
        m.put("description", p.getDescription() == null ? "" : p.getDescription());
        List<String> feats = new ArrayList<>();
        if (p.getFeatures() != null)
            for (String f : p.getFeatures().split("\\R"))
                if (!f.isBlank()) feats.add(f.trim());
        m.put("features", feats);
        m.put("featured", Boolean.TRUE.equals(p.getFeatured()));
        return m;
    }

    private void apply(Package p, Map<String, Object> body) {
        p.setName(String.valueOf(body.getOrDefault("name", "")).trim());
        p.setDescription(String.valueOf(body.getOrDefault("description", "")).trim());
        p.setPrice(new BigDecimal(String.valueOf(body.getOrDefault("price", "0"))));
        Object f = body.get("features");
        if (f instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object o : list) if (!String.valueOf(o).isBlank()) sb.append(String.valueOf(o).trim()).append("\n");
            p.setFeatures(sb.toString().trim());
        }
        p.setFeatured(Boolean.TRUE.equals(body.get("featured")));
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        ownerService.getAll().stream().findFirst().ifPresent(o ->
            packageService.getPackagesByOwner(o.getId()).forEach(p -> out.add(toMap(p))));
        return out;
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, Authentication auth) {
        Owner owner = ownerService.getLoggedInOwner(auth.getName());
        Package p = new Package();
        apply(p, body);
        p.setOwner(owner);
        return ResponseEntity.ok(toMap(packageService.createPackage(p)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") int id, @RequestBody Map<String, Object> body) {
        return packageService.getById(id).map(p -> {
            apply(p, body);
            return ResponseEntity.ok(toMap(packageService.update(p)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") int id) {
        packageService.deletePackage(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
