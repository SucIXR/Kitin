prop() {
  grep "^[[:space:]]*${1}" gradle.properties | cut -d'=' -f2 | sed 's/^[[:space:]]*//; s/\r//'
}

project_id="kitin"
project_id_b="Kitin"

commitid=$(git log --pretty='%h' -1)
mcversion=$(prop mcVersion)
grdversion=$(prop version)
preVersion=$(prop preVersion)
release_tag="$mcversion-$commitid"
jarName="$project_id-$mcversion-$commitid"
jarName_dir="kitin-server/build/libs/$jarName.jar"
make_latest=$([ $preVersion = "true" ] && echo "false" || echo "true")

#mv kitin-server/build/libs/$project_id-paperclip-$grdversion-mojmap.jar $jarName_dir
paperclip_jar=$(ls kitin-server/build/libs/$project_id-paperclip-*.jar 2>/dev/null | head -1)
if [ -z "$paperclip_jar" ]; then
  echo "ERROR: No paperclip jar found"
  exit 1
fi
mv "$paperclip_jar" "$jarName_dir"

echo "project_id=$project_id" >> $GITHUB_ENV
echo "project_id_b=$project_id_b" >> $GITHUB_ENV
echo "commit_id=$commitid" >> $GITHUB_ENV

# Logic to get commit messages since last tag
last_tag=$(git describe --tags --abbrev=0 2>/dev/null || echo "")

if [ -z "$last_tag" ]; then
  # No tags found, show last 10 commits
  logs=$(git log --pretty='> [%h] %s' -10)
else
  # Show commits since last tag
  logs=$(git log --pretty='> [%h] %s' "$last_tag..HEAD")
fi

# Write multi-line environment variable
{
  echo "commit_msg<<EOF"
  echo "$logs"
  echo "EOF"
} >> $GITHUB_ENV

echo "mcversion=$mcversion" >> $GITHUB_ENV
echo "pre=$preVersion" >> $GITHUB_ENV
echo "tag=$release_tag" >> $GITHUB_ENV
echo "jar=$jarName" >> $GITHUB_ENV
echo "jar_dir=$jarName_dir" >> $GITHUB_ENV
echo "make_latest=$make_latest" >> $GITHUB_ENV