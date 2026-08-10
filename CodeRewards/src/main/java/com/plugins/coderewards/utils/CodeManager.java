package com.plugins.coderewards.utils;

import com.plugins.coderewards.CodeRewards;
import com.plugins.coderewards.models.Code;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CodeManager {
    private final CodeRewards plugin;
    private final Map<String, Code> codes;
    private final File codesFile;
    private FileConfiguration codesConfig;

    public CodeManager(CodeRewards plugin) {
        this.plugin = plugin;
        this.codes = new HashMap<>();
        this.codesFile = new File(plugin.getDataFolder(), "codes.yml");
        loadCodes();
    }

    public void loadCodes() {
        if (!codesFile.exists()) {
            try {
                codesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create codes.yml: " + e.getMessage());
                return;
            }
        }

        codesConfig = YamlConfiguration.loadConfiguration(codesFile);
        codes.clear();

        ConfigurationSection section = codesConfig.getConfigurationSection("codes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection codeSection = section.getConfigurationSection(key);
                if (codeSection != null) {
                    Code code = Code.fromConfigSection(codeSection);
                    if (code != null) {
                        codes.put(code.getCode(), code);
                    }
                }
            }
        }

        plugin.getLogger().info("Loaded " + codes.size() + " codes.");
    }

    public void saveCodes() {
        if (codesConfig == null) {
            codesConfig = new YamlConfiguration();
        }

        codesConfig.set("codes", null);
        
        int index = 0;
        for (Code code : codes.values()) {
            String path = "codes.code" + index;
            code.toConfigSection(codesConfig, path);
            index++;
        }

        try {
            codesConfig.save(codesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save codes.yml: " + e.getMessage());
        }
    }

    public boolean addCode(Code code) {
        if (codes.containsKey(code.getCode())) {
            return false;
        }
        codes.put(code.getCode(), code);
        saveCodes();
        return true;
    }

    public Code getCode(String code) {
        return codes.get(code.toUpperCase());
    }

    public boolean removeCode(String code) {
        Code removed = codes.remove(code.toUpperCase());
        if (removed != null) {
            saveCodes();
            return true;
        }
        return false;
    }

    public Collection<Code> getAllCodes() {
        return codes.values();
    }

    public List<Code> getActiveCodes() {
        List<Code> activeCodes = new ArrayList<>();
        for (Code code : codes.values()) {
            if (code.isActive()) {
                activeCodes.add(code);
            }
        }
        return activeCodes;
    }

    public boolean codeExists(String code) {
        return codes.containsKey(code.toUpperCase());
    }

    public int getTotalCodes() {
        return codes.size();
    }

    public int getActiveCodeCount() {
        return getActiveCodes().size();
    }
}
