load("@rules_java//java:defs.bzl", "java_binary")

java_binary(
    name = "ContourCreator",
    srcs = glob(["src/*.java"]),
    deps =["@maven//:org_apache_xmlgraphics_batik_swing"]
)