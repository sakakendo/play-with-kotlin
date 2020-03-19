


### tutorials

http://sparkjava.com/tutorials/


# how to setup
```
# install sdkman
$ curl -s "https://get.sdkman.io" | bash
$ source ~/.sdkman/bin/sdkman-init.sh

# install gradle, kotlin
$ sdk install kotlin
$ sdk install gradle

```

# execute kotlin file
```
$ kotlinc hello.kt -include-runtime -d hello.jar
$ java -jar hello.jar
```

# execute spark-todo
```
cd spark-todo
./gradlew run
```
