package rustie.qqchat.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Response {
    private String text;
    private List<String> urls;

    public static Response ofText(String text) {
        return new Response(text, List.of());
    }

    public static Response of(String text, List<String> urls) {
        return new Response(text, urls == null ? List.of() : urls);
    }
}
