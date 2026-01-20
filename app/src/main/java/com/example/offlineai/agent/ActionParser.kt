package com.example.offlineai.agent.parser

import com.example.offlineai.agent.model.AgentAction
import com.example.offlineai.agent.model.AgentResponse
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Action Parser - parses model output to extract thinking and action
 * Based on MAI-UI mai_naivigation_agent.py parse_action_to_structure_output()
 */
object ActionParser {
    
    private const val SCALE_FACTOR = 999
    
    /**
     * Parse model output containing <thinking> and <tool_call> tags
     * 
     * Expected format:
     * <thinking>
     * ...reasoning process...
     * </thinking>
     * <tool_call>
     * {"name": "mobile_use", "arguments": {...}}
     * </tool_call>
     */
    fun parse(modelOutput: String): AgentResponse? {
        try {
            val normalized = normalizeOutput(modelOutput)
            val thinking = extractThinking(normalized) ?: return null
            val toolCallJson = extractToolCall(normalized) ?: return null
            val action = parseAction(toolCallJson) ?: return null
            
            return AgentResponse(thinking, action)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Normalize output format (handle </think> vs </thinking>)
     */
    private fun normalizeOutput(output: String): String {
        var normalized = output
        if (normalized.contains("</think>") && !normalized.contains("</thinking>")) {
            normalized = normalized.replace("</think>", "</thinking>")
            if (!normalized.contains("<thinking>")) {
                normalized = "<thinking>$normalized"
            }
        }
        return normalized
    }
    
    /**
     * Extract thinking content from <thinking> tags
     */
    private fun extractThinking(output: String): String? {
        val pattern = Pattern.compile("<thinking>(.*?)</thinking>", Pattern.DOTALL)
        val matcher = pattern.matcher(output)
        return if (matcher.find()) {
            matcher.group(1)?.trim()?.trim('"')
        } else {
            null
        }
    }
    
    /**
     * Extract tool_call JSON from <tool_call> tags
     */
    private fun extractToolCall(output: String): JSONObject? {
        val pattern = Pattern.compile("<tool_call>(.*?)</tool_call>", Pattern.DOTALL)
        val matcher = pattern.matcher(output)
        return if (matcher.find()) {
            val jsonStr = matcher.group(1)?.trim()?.trim('"') ?: return null
            try {
                JSONObject(jsonStr)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Parse action from tool_call JSON
     */
    private fun parseAction(toolCallJson: JSONObject): AgentAction? {
        val arguments = toolCallJson.optJSONObject("arguments") ?: return null
        val actionType = arguments.optString("action", "")
        
        return when (actionType) {
            "click" -> parseClick(arguments)
            "long_press" -> parseLongPress(arguments)
            "double_click" -> parseDoubleClick(arguments)
            "type" -> parseType(arguments)
            "swipe" -> parseSwipe(arguments)
            "open" -> parseOpen(arguments)
            "drag" -> parseDrag(arguments)
            "system_button" -> parseSystemButton(arguments)
            "wait" -> AgentAction.Wait
            "terminate" -> parseTerminate(arguments)
            "answer" -> parseAnswer(arguments)
            "ask_user" -> parseAskUser(arguments)
            else -> null
        }
    }
    
    private fun parseClick(args: JSONObject): AgentAction.Click? {
        val coord = args.optJSONArray("coordinate") ?: return null
        if (coord.length() < 2) return null
        
        val x = normalizeCoordinate(coord.getInt(0))
        val y = normalizeCoordinate(coord.getInt(1))
        
        return AgentAction.Click(x, y)
    }
    
    private fun parseLongPress(args: JSONObject): AgentAction.LongPress? {
        val coord = args.optJSONArray("coordinate") ?: return null
        if (coord.length() < 2) return null
        
        val x = normalizeCoordinate(coord.getInt(0))
        val y = normalizeCoordinate(coord.getInt(1))
        
        return AgentAction.LongPress(x, y)
    }
    
    private fun parseDoubleClick(args: JSONObject): AgentAction.DoubleClick? {
        val coord = args.optJSONArray("coordinate") ?: return null
        if (coord.length() < 2) return null
        
        val x = normalizeCoordinate(coord.getInt(0))
        val y = normalizeCoordinate(coord.getInt(1))
        
        return AgentAction.DoubleClick(x, y)
    }
    
    private fun parseType(args: JSONObject): AgentAction.Type? {
        val text = args.optString("text", "") ?: return null
        if (text.isEmpty()) return null
        return AgentAction.Type(text)
    }
    
    private fun parseSwipe(args: JSONObject): AgentAction.Swipe? {
        val directionStr = args.optString("direction", "") ?: return null
        val direction = when (directionStr.lowercase()) {
            "up" -> AgentAction.Swipe.Direction.UP
            "down" -> AgentAction.Swipe.Direction.DOWN
            "left" -> AgentAction.Swipe.Direction.LEFT
            "right" -> AgentAction.Swipe.Direction.RIGHT
            else -> return null
        }
        
        val coord = args.optJSONArray("coordinate")
        val x = coord?.optInt(0)
        val y = coord?.optInt(1)
        
        return if (x != null && y != null) {
            AgentAction.Swipe(direction, normalizeCoordinate(x), normalizeCoordinate(y))
        } else {
            AgentAction.Swipe(direction)
        }
    }
    
    private fun parseOpen(args: JSONObject): AgentAction.Open? {
        val appName = args.optString("text", "") ?: return null
        if (appName.isEmpty()) return null
        return AgentAction.Open(appName)
    }
    
    private fun parseDrag(args: JSONObject): AgentAction.Drag? {
        val startCoord = args.optJSONArray("start_coordinate") ?: return null
        val endCoord = args.optJSONArray("end_coordinate") ?: return null
        
        if (startCoord.length() < 2 || endCoord.length() < 2) return null
        
        val startX = normalizeCoordinate(startCoord.getInt(0))
        val startY = normalizeCoordinate(startCoord.getInt(1))
        val endX = normalizeCoordinate(endCoord.getInt(0))
        val endY = normalizeCoordinate(endCoord.getInt(1))
        
        return AgentAction.Drag(startX, startY, endX, endY)
    }
    
    private fun parseSystemButton(args: JSONObject): AgentAction.SystemButton? {
        val buttonStr = args.optString("button", "") ?: return null
        val button = when (buttonStr.lowercase()) {
            "back" -> AgentAction.SystemButton.Button.BACK
            "home" -> AgentAction.SystemButton.Button.HOME
            "menu" -> AgentAction.SystemButton.Button.MENU
            "enter" -> AgentAction.SystemButton.Button.ENTER
            else -> return null
        }
        return AgentAction.SystemButton(button)
    }
    
    private fun parseTerminate(args: JSONObject): AgentAction.Terminate? {
        val statusStr = args.optString("status", "") ?: return null
        val status = when (statusStr.lowercase()) {
            "success" -> AgentAction.Terminate.Status.SUCCESS
            "fail" -> AgentAction.Terminate.Status.FAIL
            else -> return null
        }
        return AgentAction.Terminate(status)
    }
    
    private fun parseAnswer(args: JSONObject): AgentAction.Answer? {
        val text = args.optString("text", "") ?: return null
        if (text.isEmpty()) return null
        return AgentAction.Answer(text)
    }
    
    private fun parseAskUser(args: JSONObject): AgentAction.AskUser? {
        val text = args.optString("text", "") ?: return null
        if (text.isEmpty()) return null
        return AgentAction.AskUser(text)
    }
    
    /**
     * Normalize coordinate from model output [0-999] to actual screen coordinate
     * This is a placeholder - actual screen dimensions will be provided at runtime
     */
    private fun normalizeCoordinate(modelCoord: Int): Int {
        // Model outputs coordinates in [0-999] range
        // This will be converted to actual screen pixels in the executor
        return modelCoord
    }
    
    /**
     * Check if model output contains agent action tags
     */
    fun containsAgentAction(output: String): Boolean {
        return output.contains("<tool_call>") && output.contains("</tool_call>")
    }
}
