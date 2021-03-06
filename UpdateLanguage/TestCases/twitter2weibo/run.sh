#########################################################################
# File Name: run.sh
# Author: Stanley Wang
# mail: stanley.chenglongwang@gmail.com
# Created Time: Mon 09 Jun 2014 01:01:39 AM PDT
#########################################################################
#!/bin/bash

rm output/*.*
touch APIFILE.APIFILE
cp twitter_to_weibo_rules.sw DONOTUSETHISNAME.DONOTUSETHISNAME
cp weibo4j.jar output/weibo4j.jar
if [ $# -gt 0 ]
then
	filename=$1
else
	filename="GetHomeTimeline_twitter.java"
fi
echo $filename
../../bin/jlc -c -ext update -classpath twitter4j.jar $filename -d output
rm DONOTUSETHISNAME.DONOTUSETHISNAME
rm APIFILE.APIFILE
