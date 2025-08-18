package runner

import org.json.JSONObject

object SweAgentRunner {
    private val configPath = System.getProperty("user.home") + "/.local/src/SWE-agent/config/default.yaml"

    /**
     * issueId must be in the form "owner/repo#number" (e.g. "flyteorg/flyte#4316").
     */
    fun run(issueId: String): JSONObject {
        // Split owner, repo and issue number
        val (repoPart, numberPart) = issueId.split('#', limit = 2).let {
            if (it.size != 2) throw IllegalArgumentException("Invalid issueId format: $issueId")
            it
        }
        val repoUrl = "https://github.com/$repoPart"
        val issueUrl = "$repoUrl/issues/$numberPart"

        // Build the SWE-agent CLI command
        val cmd = listOf(
            "/opt/anaconda3/envs/swe312/bin/sweagent",  // direct path to sweagent
            "run",
            "--quiet",                                // suppress startup banners
            "--config", configPath,
            "--agent.litellm_settings.drop_params", "true",  // strip unsupported OpenAI params
            "--agent.model.name", "gpt-3.5-turbo",
            "--env.repo.github_url", repoUrl,
            "--problem_statement.github_url", issueUrl
        )

        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()

        // Collect all output lines
        val rawOutput = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor()

        // Find the first JSON object in the output
        val jsonStart = rawOutput.indexOfFirst { it == '{' }
        if (jsonStart < 0) {
            throw IllegalStateException("No JSON object found in sweagent output:\n$rawOutput")
        }
        val jsonText = rawOutput.substring(jsonStart)

        return JSONObject(jsonText)
    }
}