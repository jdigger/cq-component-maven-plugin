package com.citytechinc.cq.component.gradle

import nebula.test.PluginProjectSpec

class ComponentPluginSpec extends PluginProjectSpec {

    @Override
    String getPluginName() {
        return ComponentPlugin.PLUGIN_ID
    }

    def "xc"() {
        def plugin = project.plugins.apply(pluginName) as ComponentPlugin

        expect:
        true
//        Projects.evaluate(project)
    }

}
