all: compile

compile:
	cd jtb-javacc-2025/project_2/minijava_example && make

run:
	cd jtb-javacc-2025/project_2/minijava_example && java Main Example.java

clean:
	cd jtb-javacc-2025/project_2/minijava_example && make clean