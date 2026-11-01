import java.nio.file.*;
import com.pianoscorefollower.app.score.MidiToMusicXml;

/** Offline fixture preparation using the same converter as the Android app. */
class ConvertScore {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("ConvertScore input.mid output.musicxml");
        Files.writeString(Path.of(args[1]), MidiToMusicXml.INSTANCE.convert(Files.readAllBytes(Path.of(args[0])),16,false));
    }
}
