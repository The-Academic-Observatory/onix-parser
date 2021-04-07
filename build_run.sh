mkdir -p pkg
mvn clean package && cp target/*.jar pkg/
java -jar ./pkg/*-shaded.jar $PWD/pkg $PWD testsrc