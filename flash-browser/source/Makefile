.PHONY: clean 

# -header '800:600:30' vs -keep

visorFreeplane.swf: visorFreeplane/*.as
	mtasc -trace no -version 8 visorFreeplane/Main.as -main -header '800:600:30' -swf visorFreeplane.swf

# visorFreeplane/Browser.as:91: characters 2-7 : type error Malformed expression
# => fixed by "-trace no"
