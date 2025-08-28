# MT-plugin – Mutation Testing Playground


Kotlin-based mutation tool that makes small code changes (rename params/locals, flip `if`s, insert comments) in a target Java repo and checks whether it still **builds**.  
Results are printed to the console and saved as JSON.  
The tool can be used in two ways:
1. As a **CLI tool** (via Gradle run)
2. As an **IntelliJ IDEA plugin** (with a custom tool window)

---

## 📂 Project Structure

```plaintext
MT-plugin/
├── .gradle/
├── .idea/
├── .run/
├── build/
├── fixtures/                      # Example Java projects used for mutation testing
│   └── java-hello/
│       ├── .gradle/
│       ├── build/
│       └── src/main/java/demo/
│           ├── App.java            # Main example file (can be simple or complex)
│           └── Target.java         # Simple mutation target for testing
│       ├── build.gradle.kts
│       └── settings.gradle.kts
├── gradle/
├── src/main/kotlin/
│  │──mtspark/        # Core mutation testing implementation
│  │     ├── BuildRunner.kt              # Handles build execution (Gradle commands)
│  │     ├── Diagnostics.kt              # Writes logs and JSON output
│  │     ├── JavaCommentInserter         # Mutation: insert comments
│  │     ├── JavaIfFlipMutator           # Mutation: flip if-conditions
│  │     ├── JavaLocalVarRenamer         # Mutation: rename local variables
│  │     ├── JavaParamRenamer            # Mutation: rename method parameters
│  │     ├── KotlinLocalVarRenamer       # Mutation for Kotlin code
│  │     ├── Main.kt                     # CLI entry point
│  │     ├── MetamorphicRenamer.kt       # Example advanced mutation
│  │     ├── Mutation.kt                 # Mutation interface/definitions
│  │     ├── RenameHelpers               # Shared rename helper functions
│  │     └── RepoManager                 # Locates and tracks files in the target repo
│  │├── org.example.mtplugin/           # IntelliJ plugin package
│  │├── runner/                         # Runner for IDE integration
│  ││   └── SweAgentRunner
│  │└── ui/                             # IntelliJ UI extensions
│       └── MtToolWindowFactory
├── .gitignore
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🏗 Architecture

### CLI Engine (`mtspark/`)
1. **CLI Entry (`Main.kt`)** – parses arguments (`--repo`, `--operation`, `--pick-index`, `--limit`, `--json-out`) and triggers a mutation run.
2. **RepoManager** – finds source files in the repo.
3. **Mutators**
    - `JavaParamRenamer` → renames method/constructor/lambda parameters
    - `JavaLocalVarRenamer` → renames locals inside blocks
    - `JavaIfFlipMutator` → flips if-conditions (semantic-preserving)
    - `JavaCommentInserter` → inserts harmless comments
4. **BuildRunner** – runs Gradle builds (before & after mutation).
5. **Diagnostics** – logs results to console and JSON (mutation details, build status, error messages).

### IntelliJ Plugin (`org.example.mtplugin/`, `runner/`, `ui/`)
- Provides a **UI tool window** (`MtToolWindowFactory`) to launch mutations inside the IDE.
- `SweAgentRunner` integrates with the build runner and mutators.
- `resources/META-INF` contains plugin descriptors for IntelliJ.
-  Defined in `resources/META-INF/plugin.xml` as an IntelliJ plugin descriptor.


---

## ⚙️ Setup

**Requirements**:
- Java 17+
- Gradle (or just use the wrapper `./gradlew`)

git clone https://github.com/leanerr/MT-plugin.git
cd MT-plugin
./gradlew clean build
---

## 🚀 Running Mutations
### Example 1: Rename a parameter
./gradlew run --args="--repo fixtures/java-hello \
--language java \
--operation rename-param \
--pick-index 2 \
--limit 1 \
--json-out /tmp/mtspark.json"

### Example 2: Rename a local variable
./gradlew run --args="--repo fixtures/java-hello \
--language java \
--operation rename-local \
--limit 1 \
--json-out /tmp/mtspark.json"


### Example 3: Flip an if-condition
./gradlew run --args="--repo fixtures/java-hello \
--language java \
--operation flip-if \
--limit 1 \
--json-out /tmp/mtspark.json"


### Example 4: Insert a comment
./gradlew run --args="--repo fixtures/java-hello \
--language java \
--operation insert-comment \
--limit 1 \
--json-out /tmp/mtspark.json"

### Continue even if build fails
./gradlew run --args="--repo fixtures/java-hello \
--language java \
--operation rename-param \
--pick-index 2 \
--limit 1 \
--json-out /tmp/mtspark.json" || true


### 1) Build the plugin
```bash
./gradlew clean build
```

### 2) Run a simple mutation
Example: rename **parameter** at index `2` in `App.java`:
```bash
./gradlew run --args="--repo /IdeaProjects/MT-plugin/fixtures/java-hello \
  --language java \
  --operation rename-param \
  --pick-index 2 \
  --limit 1 \
  --json-out /tmp/mtspark.json"
```

### 3) Continue even if build fails
```bash
./gradlew run --args="--repo /IdeaProjects/MT-plugin/fixtures/java-hello \
  --language java \
  --operation rename-param \
  --pick-index 2 \
  --limit 1 \
  --json-out /tmp/mtspark.json" || true
```
## 🧩 IntelliJ Plugin Usage

MT-plugin is also packaged as an **IntelliJ IDEA plugin**.
	The IntelliJ plugin is a wrapper/host: it gives you a UI tool window in IDEA, and when you click “Run Mutation” it calls the same mutators and SweAgentRunner.

### ▶️ Run in IntelliJ
1. Open this project in **IntelliJ IDEA (Community or Ultimate)**.
2. Use the Gradle task to launch a sandbox IDE with the plugin installed:
   
   ./gradlew runIde


✨ Features in the IDE
•	A new MT Tool Window appears (provided by MtToolWindowFactory).
•	From there, you can select a project and run mutations:
•	rename-param
•	rename-local
•	flip-if
•	insert-comment
•	Results are displayed inside the IDE, using SweAgentRunner + Diagnostics.

🛠 Edit / Extend the Plugin
•	Plugin code lives in:
•	org.example.mtplugin/
•	runner/
•	ui/
•	Update resources/META-INF/plugin.xml if you add new actions or tool windows.
•	Build a distributable plugin zip with:

   ./gradlew buildPlugin
```bash
---

## 📄 Example JSON Output (Build-breaking mutation)

```json
{
  "repo": "/IdeaProjects/MT-plugin/fixtures/java-hello",
  "language": "java",
  "operation": "rename-param",
  "target_file": "src/main/java/demo/App.java",
  "mutation": {
    "file": "src/main/java/demo/App.java",
    "old": "x",
    "new": "x_mt",
    "changed": true
  },
  "baseline_build": {
    "success": true,
    "exitCode": 0
  },
  "mutated_build": {
    "success": false,
    "exitCode": 1,
    "stderr_head": "App.java:51: error: cannot find symbol variable x_mt ..."
  }
}
```
"""


