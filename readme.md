# VM Github integration

The University of Aberdeen virtual machines do unfortunately not support git. This is why we created this java script (java is available on the VMs), 
to pull and push to git regardless

## Install

Simply download this repository as a zip by clicking on the green "Code" button on github

## How to run

Pre-compiled jar files are available in the `/target` subdirectory. You can simply run
the script using `java -jar vm-github-1.0.jar` in your console.

## Usage

The script is designed to pull a repository from git and push any changes
through pull requests. To configure the script you need to create a config
file like this one:

```txt
owner=AberdeenRSS
repo=Engineering
branch=master
token=YOUR GITHUB PAT
folder=Engineering
```

This config file configures the script to work with our [Engineering repository](https://github.com/AberdeenRSS/Engineering).
To work with your own repository change the owner and repo accordingly. `branch` determines the git branch you will be working
on. `folder` tells the script where you'd like the repository files to go. Lastly you'll need a github Personal Access Token (PAT).
Follow [this](https://medium.com/@mbohlip/how-to-generate-a-classic-personal-access-token-in-github-04985b5432c7) tutorial to find
out how to generate such a token.

### Pull

**WARNING: this will delete the any modified files in this folder**

Pulls files from the specified repository and moves them to the folder specified. Also creates a `_COMPARE`
folder, this is used by the script to determine which files you changed. Do **not** modify this folder or its
contents.

Example: `java -jar vm-github-1.0.jar pull -c your/config.txt`

### Push

Pushes files to the repository and creates a pull request (the pull request url will be printed to the console).

Example: `java -jar vm-github-1.0.jar push -c your/config.txt`