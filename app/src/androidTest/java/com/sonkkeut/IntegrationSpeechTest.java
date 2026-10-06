package com.sonkkeut;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import kr.sonkkeut.android.KoreanWhisperNative;
import kr.sonkkeut.android.WhisperByteDecoder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Explicit fixture test: uses the real release model and synthetic PCM, never mock text. */
@RunWith(AndroidJUnit4.class)
public class IntegrationSpeechTest {
    @Test public void ownWhisperWithMenuContextTranscribesOrder() throws Exception {
        String fixture = InstrumentationRegistry.getArguments().getString("speechFixture");
        assumeTrue("Pass speechFixture with verified public weights and synthetic order.raw", fixture != null);
        File directory = new File(fixture);
        byte[] bytes = Files.readAllBytes(new File(directory, "order.raw").toPath());
        short[] pcm = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
        assertEquals(88141, pcm.length);
        File model = new File(directory, "model");
        WhisperByteDecoder decoder = new WhisperByteDecoder(new File(model, "vocabulary.json"));
        long handle = KoreanWhisperNative.INSTANCE.create(model.getAbsolutePath(), 2, true);
        assertNotEquals(0L, handle);
        try {
            int[] hints = decoder.encode(" 아메리카노 카페라떼 아이스 따뜻한 톨 그란데 벤티 포장 매장");
            JSONObject result = new JSONObject(KoreanWhisperNative.INSTANCE.transcribe(handle, pcm, decoder.tokenPieces(hints)));
            JSONArray tokens = result.getJSONArray("token_ids");
            int[] ids = new int[tokens.length()];
            for (int i = 0; i < ids.length; i++) ids[i] = tokens.getInt(i);
            assertEquals("따뜻한 아메리카노 두 잔 하고 카페라떼 한 잔 포장해 주세요.", decoder.decode(ids));
            assertTrue(result.getDouble("no_speech_probability") < 0.1);
            assertTrue(result.getLong("inference_ms") > 0);
            assertTrue(Double.isFinite(result.getDouble("sequence_score")));
        } finally {KoreanWhisperNative.INSTANCE.close(handle);}
    }
}
