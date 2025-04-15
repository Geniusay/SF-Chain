package com.sfchain.examples;

import com.sfchain.core.constant.AIConstant;
import com.sfchain.core.model.AIModel;
import com.sfchain.core.model.ModelParameters;
import com.sfchain.core.registry.ModelRegistry;
import com.sfchain.spring.service.AIService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 描述: SFChain增强版示例应用
 * @author suifeng
 * 日期: 2025/4/15
 */
@SpringBootApplication
public class SFChainExampleApplication {

    @Value("${sfchain.default-model:deepseek-chat}")
    private String defaultModel;

    public static void main(String[] args) {
        SpringApplication.run(SFChainExampleApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(AIService aiService, ModelRegistry modelRegistry) {
        return args -> {
            printWelcomeMessage();

            // 获取所有可用模型
            Map<String, AIModel> availableModels = modelRegistry.getAllModels();

            // 当前使用的模型
            AtomicReference<String> currentModel = new AtomicReference<>(defaultModel);

            // 会话历史
            List<Map<String, String>> conversationHistory = new ArrayList<>();

            Scanner scanner = new Scanner(System.in);

            while (true) {
                printPrompt(currentModel.get());
                String input = scanner.nextLine().trim();

                // 处理命令或生成内容
                if (input.isEmpty()) {
                    continue;
                } else if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                    break;
                } else if (input.equalsIgnoreCase("help")) {
                    printHelpMessage();
                    continue;
                } else if (input.equalsIgnoreCase("models")) {
                    printAvailableModels(availableModels);
                    continue;
                } else if (input.equalsIgnoreCase("clear")) {
                    clearConversation(conversationHistory);
                    continue;
                } else if (input.startsWith("use ")) {
                    String modelName = input.substring(4).trim();
                    if (switchModel(modelName, availableModels, currentModel)) {
                        System.out.println("Switched to model: " + currentModel.get());
                    }
                    continue;
                } else if (input.startsWith("temp ")) {
                    try {
                        double temp = Double.parseDouble(input.substring(5).trim());
                        setTemperature(temp, currentModel.get(), availableModels);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid temperature value. Please enter a number between 0.0 and 2.0");
                    }
                    continue;
                }

                // 记录用户输入
                Map<String, String> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", input);
                conversationHistory.add(userMessage);

                try {
                    // 准备参数
                    Map<String, Object> params = new HashMap<>();
                    params.put("prompt", input);

                    // 如果有会话历史，添加到参数中
                    if (conversationHistory.size() > 1) {
                        params.put("history", conversationHistory);
                    }

                    // 执行生成
                    long startTime = System.currentTimeMillis();
                    String response = aiService.execute("text-generation", currentModel.get(), params);
                    long endTime = System.currentTimeMillis();

                    // 记录AI响应
                    Map<String, String> aiMessage = new HashMap<>();
                    aiMessage.put("role", "assistant");
                    aiMessage.put("content", response);
                    conversationHistory.add(aiMessage);

                    // 打印响应
                    System.out.println("\n" + formatResponse(response));
                    System.out.printf("(Generated in %.2f seconds with %s)\n",
                            (endTime - startTime) / 1000.0, currentModel.get());

                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                    // 如果出错，移除最后一条用户消息
                    if (!conversationHistory.isEmpty()) {
                        conversationHistory.remove(conversationHistory.size() - 1);
                    }
                }
            }

            System.out.println("Thank you for using SFChain! Goodbye!");
        };
    }

    /**
     * 打印欢迎信息
     */
    private void printWelcomeMessage() {
        System.out.println("┌───────────────────────────────────────────────┐");
        System.out.println("│             🤖 SFChain AI Assistant            │");
        System.out.println("└───────────────────────────────────────────────┘");
        System.out.println("Welcome! I'm your AI assistant powered by SFChain.");
        System.out.println("Type 'help' to see available commands.");
        System.out.println("Default model: " + defaultModel);
        System.out.println();
    }

    /**
     * 打印提示符
     */
    private void printPrompt(String currentModel) {
        System.out.print("\n[" + currentModel + "] > ");
    }

    /**
     * 打印帮助信息
     */
    private void printHelpMessage() {
        System.out.println("\n📋 Available Commands:");
        System.out.println("  help    - Show this help message");
        System.out.println("  models  - List all available models");
        System.out.println("  use X   - Switch to model X (e.g., 'use gpt-4o')");
        System.out.println("  temp X  - Set temperature to X (e.g., 'temp 0.8')");
        System.out.println("  clear   - Clear conversation history");
        System.out.println("  exit    - Exit the application");
        System.out.println();
    }

    /**
     * 打印可用模型
     */
    private void printAvailableModels(Map<String, AIModel> models) {
        System.out.println("\n🤖 Available Models:");

        // 按类型分组显示模型
        Map<String, List<AIModel>> modelsByType = new HashMap<>();

        // 分组
        models.values().forEach(model -> {
            String type = getModelType(model.getName());
            modelsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(model);
        });

        // 按类型显示
        modelsByType.forEach((type, modelList) -> {
            System.out.println("\n  " + type + ":");
            modelList.forEach(model ->
                    System.out.printf("    %-20s - %s\n", model.getName(), model.description()));
        });

        System.out.println();
    }

    /**
     * 获取模型类型
     */
    private String getModelType(String modelName) {
        if (modelName.startsWith("gpt")) {
            return "OpenAI Models";
        } else if (modelName.startsWith("deepseek")) {
            return "DeepSeek Models";
        } else if (modelName.startsWith("qwen")) {
            return "Qwen Models";
        } else if (modelName.equals(AIConstant.TELE_AI)) {
            return "TeleAI Models";
        } else if (modelName.equals(AIConstant.THUDM)) {
            return "THUDM Models";
        } else {
            return "Other Models";
        }
    }

    /**
     * 切换模型
     */
    private boolean switchModel(String modelName, Map<String, AIModel> availableModels, AtomicReference<String> currentModel) {
        if (availableModels.containsKey(modelName)) {
            currentModel.set(modelName);
            return true;
        } else {
            System.err.println("Model not found: " + modelName);
            System.out.println("Use 'models' command to see available models.");
            return false;
        }
    }

    /**
     * 设置温度参数
     */
    private void setTemperature(double temperature, String modelName, Map<String, AIModel> availableModels) {
        if (temperature < 0.0 || temperature > 2.0) {
            System.err.println("Temperature must be between 0.0 and 2.0");
            return;
        }

        AIModel model = availableModels.get(modelName);
        if (model != null) {
            ModelParameters params = model.getParameters();
            params.temperature(temperature);
            model.withParameters(params);
            System.out.printf("Temperature for %s set to %.1f\n", modelName, temperature);
        } else {
            System.err.println("Model not found: " + modelName);
        }
    }

    /**
     * 清除会话历史
     */
    private void clearConversation(List<Map<String, String>> history) {
        history.clear();
        System.out.println("Conversation history cleared.");
    }

    /**
     * 格式化响应文本
     */
    private String formatResponse(String response) {
        // 添加一些简单的格式化，例如代码块
        StringBuilder formatted = new StringBuilder();
        String[] lines = response.split("\n");
        boolean inCodeBlock = false;

        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                if (inCodeBlock) {
                    formatted.append("\n┌─────────── Code ───────────┐\n");
                } else {
                    formatted.append("\n└─────────────────────────────┘\n");
                }
            } else {
                if (inCodeBlock) {
                    formatted.append("│ ").append(line).append("\n");
                } else {
                    formatted.append(line).append("\n");
                }
            }
        }

        return formatted.toString();
    }
}