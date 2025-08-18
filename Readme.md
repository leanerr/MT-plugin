# MT-plugin – Mutation Testing Playground

Kotlin-based mutation tool that makes small code changes (rename params/locals, flip `if`s, insert comments) in a target Java repo and checks whether it still **builds**. Results are printed to the console and saved as JSON.

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
├── src/main/kotlin/mtspark/        # Core mutation testing implementation
│   ├── BuildRunner.kt              # Handles build execution (Gradle commands)
│   ├── Diagnostics.kt              # Writes logs and JSON output
│   ├── JavaCommentInserter         # Mutation: insert comments
│   ├── JavaIfFlipMutator           # Mutation: flip if-conditions
│   ├── JavaLocalVarRenamer         # Mutation: rename local variables
│   ├── JavaParamRenamer            # Mutation: rename method parameters
│   ├── KotlinLocalVarRenamer       # Mutation for Kotlin code
│   ├── Main.kt                     # CLI entry point
│   ├── MetamorphicRenamer.kt       # Example advanced mutation
│   ├── Mutation.kt                 # Mutation interface/definitions
│   ├── RenameHelpers               # Shared rename helper functions
│   └── RepoManager                 # Locates and tracks files in the target repo
├── .gitignore
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🏗 Architecture

**1. CLI Entry (`Main.kt`)**  
Parses arguments (`--repo`, `--operation`, `--pick-index`, `--limit`, `--json-out`) and triggers a mutation run.

**2. RepoManager**  
Finds source files in the given repository and keeps track of mutation targets.

**3. Mutators**
- `JavaParamRenamer` → renames method parameters
- `JavaLocalVarRenamer` → renames local variables
- `JavaIfFlipMutator` → flips if-conditions
- `JavaCommentInserter` → adds comments
- (plus Kotlin equivalents)

**4. BuildRunner**  
Runs the target project’s build (via Gradle) both **before** and **after** mutation to check if it still compiles.

**5. Diagnostics**  
Logs results to console and writes a detailed JSON report with:
- Mutation details
- Build status (success/failure)
- Error messages

---

## ⚙️ Setup

**Requirements**:
- Java 17+
- Gradle (or just use the wrapper `./gradlew`)

---

## 🚀 Running Mutations

### 1) Build the plugin
```bash
./gradlew clean build
```

### 2) Run a simple mutation
Example: rename **parameter** at index `2` in `App.java`:
```bash
./gradlew run --args="--repo /Users/aliasgari/IdeaProjects/MT-plugin/fixtures/java-hello \
  --language java \
  --operation rename-param \
  --pick-index 2 \
  --limit 1 \
  --json-out /tmp/mtspark.json"
```

### 3) Continue even if build fails
```bash
./gradlew run --args="--repo /Users/aliasgari/IdeaProjects/MT-plugin/fixtures/java-hello \
  --language java \
  --operation rename-param \
  --pick-index 2 \
  --limit 1 \
  --json-out /tmp/mtspark.json" || true
```

---

## 📄 Example JSON Output (Build-breaking mutation)

```json
{
  "repo": "/Users/aliasgari/IdeaProjects/MT-plugin/fixtures/java-hello",
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

