
uber: bin/netmon-uber.jar 
pgd:  bin/netmon-guard.jar

bin/netmon.jar: netmon.scala
	mkdir -p bin 
	scalac netmon.scala -d bin/netmon.jar 

bin/netmon-uber.jar: bin/netmon.jar
	jarget uber -scala -o bin/netmon-uber.jar -m bin/netmon.jar -r resources

# Optmized with Proguard 
bin/netmon-guard.jar: bin/netmon-uber.jar config.pro 
	java -jar proguard.jar @config.pro

clean:
	rm -rf bin/*.jar 
