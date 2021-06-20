

# BUILD

jbang export portable --force JarShader.java;  jbang run JarShader.java JarShader.jar; \
mv target/test-uber.jar test-uber.jar; \
java -jar ./test-uber.jar JarShader.jar
