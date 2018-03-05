all: run
uber: bin/netmon-uber.jar 
pgd:  bin/netmon-guard.jar

src := src/netmon.scala src/netmon.Display.scala src/netmon-main.scala

bin/netmon.jar: $(src)
	mkdir -p bin 
	fsc $(src)  -d bin/netmon.jar 

force: $(src)
	mkdir -p bin 
	scalac $(src)  -d bin/netmon.jar 

run: bin/netmon.jar
	scala bin/netmon.jar

bin/netmon-uber.jar: bin/netmon.jar
	jarget uber -scala -o bin/netmon-uber.jar -m bin/netmon.jar -r resources

# Optmized with Proguard 
bin/netmon-guard.jar: bin/netmon-uber.jar config.pro 
	java -jar proguard.jar @config.pro

clean:
	rm -rf bin/*.jar 
