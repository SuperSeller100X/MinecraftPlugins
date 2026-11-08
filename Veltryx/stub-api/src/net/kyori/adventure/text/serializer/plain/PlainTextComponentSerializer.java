package net.kyori.adventure.text.serializer.plain;
public interface PlainTextComponentSerializer {
 static PlainTextComponentSerializer plainText(){return c -> c instanceof net.kyori.adventure.text.StubComponent s ? s.content : "";}
 String serialize(net.kyori.adventure.text.Component c);
}
