#!/bin/bash
cp -Rvf build/resources/site gh-pages
cd gh-pages
git init
git config user.name "travis-ci"
git config user.email "travis@travis-ci.org"
git add .
git commit -m "Publishing site from Travis CI build $TRAVIS_BUILD_NUMBER"
git push --force --quiet "https://${GH_TOKEN}@${GH_REF}" master:gh-pages > /dev/null 2>&1
echo "Published site to gh-pages.  See http://aweigold.github.io/lemming"
