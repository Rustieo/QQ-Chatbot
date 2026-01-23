package rustie.qqchat.agent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Component
@RequiredArgsConstructor
public final class ToolRegistry {
    private final Map<String, Tool> toolsByName=new ConcurrentHashMap<>();
    private final ListableBeanFactory  beanFactory;

    @PostConstruct
    public void init() {
        var tools=beanFactory.getBeansOfType(Tool.class).values();
        for (var tool : tools) toolsByName.put(tool.name(),tool);
    }

    public List<Tool> all() {
        return List.copyOf(toolsByName.values());
    }

    public Tool get(String name) {
        return toolsByName.get(name);
    }

}

