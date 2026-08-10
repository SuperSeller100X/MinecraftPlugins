package com.example.coderewards.data;

import com.example.coderewards.CodeRewardsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CodeManager {
    private final CodeRewardsPlugin plugin;
    private final Map<String, RewardCode> codes;
    private File codesFile;
    private FileConfiguration codesConfig;

    public CodeManager(CodeRewardsPlugin plugin) {
        this.plugin = plugin;
        this.codes = new HashMap<>();
        loadCodes();
    }

    public void loadCodes() {
        codesFile = new File(plugin.getDataFolder(), "codes.yml");
        if (!codesFile.exists()) {
            plugin.saveResource("codes.yml", false);
        }
        codesConfig = YamlConfiguration.loadConfiguration(codesFile);
        
        codes.clear();
        ConfigurationSection section = codesConfig.getConfigurationSection("codes");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection codeSection = section.getConfigurationSection(key);
                if (codeSection != null) {
                    try {
                        Map<String, Object> codeData = codeSection.getValues(false);
                        // Convert nested sections properly
                        Map<String, Object> serializedMap = new HashMap<>();
                        for (Map.Entry<String, Object> entry : codeData.entrySet()) {
                            if (entry.getValue() instanceof ConfigurationSection) {
                                serializedMap.put(entry.getKey(), ((ConfigurationSection) entry.getValue()).getValues(false));
                            } else if (entry.getValue() instanceof List) {
                                List<?> list = (List<?>) entry.getValue();
                                List<Map<String, Object>> complexList = new ArrayList<>();
                                boolean hasSections = false;
                                for (Object obj : list) {
                                    if (obj instanceof ConfigurationSection) {
                                        complexList.add(((ConfigurationSection) obj).getValues(false));
                                        hasSections = true;
                                    }
                                }
                                if (hasSections) {
                                    serializedMap.put(entry.getKey(), complexList);
                                } else {
                                    serializedMap.put(entry.getKey(), (List<String>) list);
                                }
                            } else {
                                serializedMap.put(entry.getKey(), entry.getValue());
                            }
                        }
                        RewardCode code = RewardCode.deserialize(serializedMap);
                        codes.put(code.getCode().toLowerCase(), code);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load code: " + key);
                        e.printStackTrace();
                    }
                }
            }
        }
        plugin.getLogger().info("Loaded " + codes.size() + " reward codes.");
    }

    public void saveCodes() {
        if (codesConfig == null) {
            codesConfig = new YamlConfiguration();
        }
        
        codesConfig.set("codes", null);
        for (RewardCode code : codes.values()) {
            String path = "codes." + code.getCode();
            Map<String, Object> serialized = code.serialize();
            for (Map.Entry<String, Object> entry : serialized.entrySet()) {
                codesConfig.set(path + "." + entry.getKey(), entry.getValue());
            }
        }
        
        try {
            codesConfig.save(codesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save codes.yml!");
            e.printStackTrace();
        }
    }

    public RewardCode getCode(String code) {
        return codes.get(code.toLowerCase());
    }

    public boolean addCode(RewardCode code) {
        if (codes.containsKey(code.getCode().toLowerCase())) {
            return false;
        }
        codes.put(code.getCode().toLowerCase(), code);
        saveCodes();
        return true;
    }

    public boolean removeCode(String code) {
        RewardCode removed = codes.remove(code.toLowerCase());
        if (removed != null) {
            saveCodes();
        }
        return removed != null;
    }

    public Collection<RewardCode> getAllCodes() {
        return codes.values();
    }

    public boolean isValidCode(String code) {
        RewardCode rewardCode = getCode(code);
        return rewardCode != null && rewardCode.canRedeem();
    }
}
