# ZipDiff

Tool for listing, storing and resolving differences between zip files:

- Compare two zip files and list the differences.
- Generate a '.zpatch' file containing the differences between two zip files.
- Generate a new zip file from an original zip file and a '.zpatch' file.

The functionality is available from the command-line (using java -jar) as well as via API calls.

Files are compared by using the paths and CRC values in the zip.


### Command line options

```
-f, --base-file <name>      Base zip file
-c, --compare-with <name>   Zip file to compare with
-g, --generate-patch <name> Generates patch file instead of listing the differences
-p, --patch-with <name>     File (.zpatch) to patch given -file with
-t, --patch-to <name>       File holding the patch result (opposite of --generate-patch)
-i, --ignore-validation     Skips testing patch result (crc check)
-v, --verbose               Shows a bit more info
```

#### Possible actions:

(All examples omit the command prefix: ```java -jar zipdiff.jar```)

__Compare two zip files and list the differences:__  
  ```--base-file old.zip --compare-with new.zip```

__Compare two zip files and generate a patch file containing the differences:__  
  ```--base-file old.zip --compare-with new.zip --generate-patch oldToNew```

__Patch an existing zip file to a new zip file:__  
  ```--base-file old.zip --patch-with oldToNew.zpatch --patch-to new.zip```

### API

A ```ZipDiff``` class exists with the following static methods on it:

- ```List<ZipChange> compareZips(File fOld, File fNew)```
- ```void generatePatchFile(File fOld, File fNew, File patch)```
- ```void generatePatchedZipFrom(File fOld, File patch)```
