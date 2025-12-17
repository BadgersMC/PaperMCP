# Build Instructions

This project uses Gradle with a wrapper, so you don't need to install Gradle manually. The wrapper works on all platforms (Windows, macOS, Linux).

## Quick Start

### Build Everything (Plugin + Client)

**Linux/macOS:**
```bash
./gradlew build
```

**Windows:**
```cmd
gradlew.bat build
```

This will:
- Download Gradle automatically (first time only)
- Build both plugin and client
- Run tests

### Build Plugin Only

```bash
./gradlew :plugin:build      # Linux/macOS
gradlew.bat :plugin:build    # Windows
```

### Build Client Only

```bash
./gradlew :client:build      # Linux/macOS
gradlew.bat :client:build    # Windows
```

## Requirements

- **Java 21** - Required for building the plugin (Minecraft 1.21+ requirement)
- **Internet connection** - To download Gradle and dependencies (first time only)

You can verify your Java version:
```bash
java -version
```

If you need Java 21, download from: https://adoptium.net/

## Output Locations

After successful build, find your JARs here:

```
plugin/build/libs/PaperMCP-Plugin-1.0.0-SNAPSHOT.jar  ← Upload to Minecraft server
client/build/libs/PaperMCP-Client-1.0.0-SNAPSHOT.jar  ← Use on your PC with Claude
```

## Clean Build

To clean and rebuild from scratch:

**Linux/macOS:**
```bash
./gradlew clean build
```

**Windows:**
```cmd
gradlew.bat clean build
```

## Troubleshooting

### "Java not found"

1. Install Java 21: https://adoptium.net/
2. Verify installation: `java -version`
3. Ensure JAVA_HOME is set (optional but recommended)

### "Permission denied" (Linux/macOS)

Make the Gradle wrapper executable:
```bash
chmod +x gradlew
```

### Build Fails with Kotlin Errors

Make sure you're using Java 21:
```bash
java -version
```

The plugin specifically requires Java 21 for Paper 1.21+ compatibility.

### Clean Build Cache

Sometimes the Gradle cache needs clearing:

```bash
rm -rf .gradle build plugin/build client/build    # Linux/macOS
rd /s /q .gradle build plugin\build client\build  # Windows (cmd)
```

Then rebuild with `./gradlew build`

## Using an IDE

### IntelliJ IDEA (Recommended)

1. Open the project folder in IntelliJ IDEA
2. IDEA will automatically detect the Gradle project
3. Use the Gradle panel on the right:
   - `plugin → Tasks → build → build`
   - `client → Tasks → build → build`

### VS Code

1. Install the "Extension Pack for Java"
2. Open the project folder
3. Use the Gradle panel in the sidebar

### Eclipse

1. Import as "Existing Gradle Project"
2. Use the Gradle tasks view to build

## Next Steps

After building, see **SETUP_GUIDE.md** for installation and configuration instructions!
