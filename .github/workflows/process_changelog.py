import re
import subprocess

input_file = "changelog.md"
output_file = "changelog-output.md"

def fetch_test_and_optional_dependencies():
    # Fetch the list of all subprojects
    result = subprocess.run(
        ["./gradlew", "projects"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    subprojects = []
    for line in result.stdout.splitlines():
        match = re.match(r".*Project (':.+')", line)
        if match:
            subprojects.append(match.group(1).strip("'"))

    print(f"Found the following subprojects\n\n {subprojects}\n\n")
    test_optional_dependencies = set()
    implementation_dependencies = set()

    print("Will fetch non transitive dependencies for all subprojects...")
    # Run dependencies task for all subprojects in a single Gradle command
    if subprojects:
        dependencies_command = ["./gradlew"] + [f"{subproject}:dependencies" for subproject in subprojects]
        result = subprocess.run(
            dependencies_command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        in_test_section = False
        in_optional_section = False
        in_implementation_section = False

        for line in result.stdout.splitlines():
            if "project :" in line:
                continue

            # Detect gradle plugin
            if "classpath" in line:
                in_optional_section = True
                continue

            # Detect test dependencies section
            if "testCompileClasspath" in line or "testImplementation" in line:
                in_test_section = True
                continue
            if "runtimeClasspath" in line or line.strip() == "":
                in_test_section = False

            # Detect optional dependencies section
            if "compileOnly" in line:
                in_optional_section = True
                continue
            if line.strip() == "":
                in_optional_section = False

            # Detect implementation dependencies section
            if "implementation" in line or "compileClasspath" in line:
                in_implementation_section = True
                continue
            if line.strip() == "":
                in_implementation_section = False

            # Parse dependencies explicitly declared with +--- or \---
            match = re.match(r"[\\+|\\\\]--- ([^:]+):([^:]+):([^ ]+)", line)
            if match:
                group_id, artifact_id, _ = match.groups()
                dependency_key = f"{group_id}:{artifact_id}"
                if in_test_section or in_optional_section:
                    test_optional_dependencies.add(dependency_key)
                if in_implementation_section:
                    implementation_dependencies.add(dependency_key)

    # Remove dependencies from test/optional if they are also in implementation
    final_exclusions = test_optional_dependencies - implementation_dependencies

    print(f"Dependencies in either test or optional scope to be excluded from changelog processing:\n\n{final_exclusions}\n\n")
    return final_exclusions

def process_dependency_upgrades(lines, exclude_dependencies):
    dependencies = {}
    regex = re.compile(r"- Bump (.+?) from ([\d\.]+) to ([\d\.]+) \[(#[\d]+)\]\((.+)\)")
    for line in lines:
        match = regex.match(line)
        if match:
            unit, old_version, new_version, pr_number, link = match.groups()
            if unit not in exclude_dependencies:
                if unit not in dependencies:
                    dependencies[unit] = {"lowest": old_version, "highest": new_version, "pr_number": pr_number, "link": link}
                else:
                    dependencies[unit]["lowest"] = min(dependencies[unit]["lowest"], old_version)
                    dependencies[unit]["highest"] = max(dependencies[unit]["highest"], new_version)
    sorted_units = sorted(dependencies.keys())
    return [f"- Bump {unit} from {dependencies[unit]['lowest']} to {dependencies[unit]['highest']} [{dependencies[unit]['pr_number']}]({dependencies[unit]['link']})" for unit in sorted_units]

with open(input_file, "r") as file:
    lines = file.readlines()

# Fetch test and optional dependencies from all projects
print("Fetching test and optional dependencies from the project and its subprojects...")
exclude_dependencies = fetch_test_and_optional_dependencies()

# Step 1: Copy all content until the hammer line
header = []
dependency_lines = []
footer = []
in_dependency_section = False

print("Parsing changelog until the dependency upgrades section...")

for line in lines:
    if line.startswith("## :hammer: Dependency Upgrades"):
        in_dependency_section = True
        header.append(line)
        header.append("\n")
        break
    header.append(line)

print("Parsing dependency upgrade section...")

# Step 2: Parse dependency upgrades
if in_dependency_section:
    for line in lines[len(header):]:
        if line.startswith("## :heart: Contributors"):
            break
        dependency_lines.append(line)

print("Parsing changelog to find everything after the dependency upgrade section...")
# Find the footer starting from the heart line
footer_start_index = next((i for i, line in enumerate(lines) if line.startswith("## :heart: Contributors")), None)
if footer_start_index is not None:
    footer = lines[footer_start_index:]

print("Processing the dependency upgrades section...")
processed_dependencies = process_dependency_upgrades(dependency_lines, exclude_dependencies)

print("Writing output...")
# Step 3: Write the output file
with open(output_file, "w") as file:
    file.writelines(header)
    file.writelines(f"{line}\n" for line in processed_dependencies)
    file.writelines("\n")
    file.writelines(footer)
