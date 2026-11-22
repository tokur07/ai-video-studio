package com.aivideostudio.app.storage

import android.content.Context
import com.aivideostudio.app.generator.LocalTemplateGenerator
import com.aivideostudio.app.model.GeneratedScript
import com.aivideostudio.app.model.GeneratorConfig
import com.aivideostudio.app.model.ProjectContentBundle
import com.aivideostudio.app.model.StoryboardScene
import org.json.JSONArray
import org.json.JSONObject

object GeneratorStorage {

    private const val PREFS_NAME = "ai_video_studio"
    private fun keyForProject(projectId: Long): String = "v03_bundle_$projectId"

    fun loadBundle(context: Context, projectId: Long, fallbackTitle: String = "", fallbackType: String = "Edukasi & Tutorial"): ProjectContentBundle {
        val defaultBundle = ProjectContentBundle(
            projectId = projectId,
            config = GeneratorConfig(topic = fallbackTitle, videoType = fallbackType)
        )
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(keyForProject(projectId), null) ?: return defaultBundle
            val root = JSONObject(raw)

            val cfgObj = root.optJSONObject("config") ?: JSONObject()
            val config = GeneratorConfig(
                topic = cfgObj.optString("topic", fallbackTitle),
                videoType = cfgObj.optString("videoType", fallbackType),
                language = cfgObj.optString("language", "Indonesia"),
                targetDurationSec = cfgObj.optInt("targetDurationSec", 60),
                sceneCount = cfgObj.optInt("sceneCount", 4),
                narrationStyle = cfgObj.optString("narrationStyle", "Informatif & Ramah"),
                targetAudience = cfgObj.optString("targetAudience", "Pemula / Umum"),
                aspectRatio = cfgObj.optString("aspectRatio", "9:16"),
                detailLevel = cfgObj.optString("detailLevel", "Standar")
            )

            val scrObj = root.optJSONObject("script") ?: JSONObject()
            val narrArr = scrObj.optJSONArray("sceneNarrations") ?: JSONArray()
            val sceneNarrations = (0 until narrArr.length()).map { narrArr.optString(it, "") }
            val script = GeneratedScript(
                opening = scrObj.optString("opening", ""),
                body = scrObj.optString("body", ""),
                closing = scrObj.optString("closing", ""),
                sceneNarrations = sceneNarrations,
                estimatedDurationSec = scrObj.optInt("estimatedDurationSec", config.targetDurationSec),
                storyStructure = scrObj.optString("storyStructure", ""),
                isManuallyEdited = scrObj.optBoolean("isManuallyEdited", false),
                sourceLabel = scrObj.optString("sourceLabel", LocalTemplateGenerator.GENERATOR_LABEL)
            )

            val scArr = root.optJSONArray("scenes") ?: JSONArray()
            val scenes = (0 until scArr.length()).mapIndexed { idx, _ ->
                val s = scArr.getJSONObject(idx)
                StoryboardScene(
                    sceneNumber = s.optInt("sceneNumber", idx + 1),
                    title = s.optString("title", "Adegan ${idx + 1}"),
                    durationSec = s.optInt("durationSec", 15),
                    narration = s.optString("narration", ""),
                    visualDescription = s.optString("visualDescription", ""),
                    cameraDirection = s.optString("cameraDirection", ""),
                    visualPrompt = s.optString("visualPrompt", ""),
                    transition = s.optString("transition", "Cut Tajam"),
                    audioDirection = s.optString("audioDirection", "")
                )
            }

            ProjectContentBundle(
                projectId = root.optLong("projectId", projectId),
                config = config,
                script = script,
                scenes = scenes,
                updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            defaultBundle
        }
    }

    fun saveBundle(context: Context, bundle: ProjectContentBundle) {
        val cfgJson = JSONObject().apply {
            put("topic", bundle.config.topic)
            put("videoType", bundle.config.videoType)
            put("language", bundle.config.language)
            put("targetDurationSec", bundle.config.targetDurationSec)
            put("sceneCount", bundle.config.sceneCount)
            put("narrationStyle", bundle.config.narrationStyle)
            put("targetAudience", bundle.config.targetAudience)
            put("aspectRatio", bundle.config.aspectRatio)
            put("detailLevel", bundle.config.detailLevel)
        }

        val narrArray = JSONArray()
        bundle.script.sceneNarrations.forEach { narrArray.put(it) }

        val scrJson = JSONObject().apply {
            put("opening", bundle.script.opening)
            put("body", bundle.script.body)
            put("closing", bundle.script.closing)
            put("sceneNarrations", narrArray)
            put("estimatedDurationSec", bundle.script.estimatedDurationSec)
            put("storyStructure", bundle.script.storyStructure)
            put("isManuallyEdited", bundle.script.isManuallyEdited)
            put("sourceLabel", bundle.script.sourceLabel)
        }

        val scenesArray = JSONArray()
        bundle.scenes.forEach { scene ->
            scenesArray.put(JSONObject().apply {
                put("sceneNumber", scene.sceneNumber)
                put("title", scene.title)
                put("durationSec", scene.durationSec)
                put("narration", scene.narration)
                put("visualDescription", scene.visualDescription)
                put("cameraDirection", scene.cameraDirection)
                put("visualPrompt", scene.visualPrompt)
                put("transition", scene.transition)
                put("audioDirection", scene.audioDirection)
            })
        }

        val rootJson = JSONObject().apply {
            put("projectId", bundle.projectId)
            put("config", cfgJson)
            put("script", scrJson)
            put("scenes", scenesArray)
            put("updatedAt", System.currentTimeMillis())
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(keyForProject(bundle.projectId), rootJson.toString())
            .apply()
    }
}
