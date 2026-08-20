description = "PumpeCraft trader plugin"

dependencies {
    compileOnly(project(":plugins:transactions"))
    add("ideClasspath", project(":plugins:transactions"))
}
