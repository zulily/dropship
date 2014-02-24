Dropship
===

Deploy, instantiate, and run your Java applications from any maven repository.

Getting Started
----

Stop pushing artifacts into production, use Dropship to pull them down from a maven repository and run your code!
Dropship automatically creates a classpath containing all of your project's dependencies and will run the `public static
void main(String[])` method of a class you specify!

    java -jar dropship.jar mygroup:myartifact[:myversion] mygroup.myartifact.Main args...

If you omit the version, Dropship will automatically run the latest version of your artifact.

If you need to manage versions of multiple artifacts, then use `dropship.properties` to map them.

    #dropship.properties
    repo.remote.url = http://some-other-repo/
    repo.local.path = /tmp

    dropship.additional.paths = /tmp/resources

    # You can leave older entries, they will be ignored and you can use this as a deploy log
    # 2012-12-23
    mygroup.myartifact = 1.0

    # 2012-12-24
    mygroup.myartifact = 1.1


License
---

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
