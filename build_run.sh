mkdir -p pkg
mvn clean package && cp target/*.jar pkg/
java -cp "./pkg/*" academy.observatory.app.OnixParser $PWD/pkg $PWD
