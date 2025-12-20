echo "project_id=kitin" >> $GITHUB_ENV
echo "project_id_b=Kitin" >> $GITHUB_ENV
echo "mcversion=1.21.1" >> $GITHUB_ENV
echo "tag=$(date +'%Y%m%d%H%M')" >> $GITHUB_ENV
echo "commit_id=$(git rev-parse --short HEAD)" >> $GITHUB_ENV
echo "jar_dir=kitin-server/build/libs/*-paperclip-*-mojmap.jar" >> $GITHUB_ENV