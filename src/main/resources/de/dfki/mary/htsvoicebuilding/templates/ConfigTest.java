package marytts.voice.${voice.nameCamelCase};

import static org.junit.Assert.*;

import marytts.config.MaryConfig;
import marytts.config.VoiceConfig;
import marytts.exceptions.MaryConfigurationException;

import org.junit.Test;

/**
 * @author marc
 *
 */
public class ConfigTest {
	private static final String voiceName = "${voice.name}";

	@Test
	public void isNotMainConfig() throws MaryConfigurationException {
		MaryConfig m = new Config();
		assertFalse(m.isMainConfig());
	}

	@Test
	public void isVoiceConfig() throws MaryConfigurationException {
		MaryConfig m = new Config();
		assertTrue(m.isVoiceConfig());
	}

	@Test
	public void hasRightName() throws MaryConfigurationException {
		VoiceConfig m = new Config();
		assertEquals(voiceName, m.getName());
	}

	@Test
	public void canGetByName() throws MaryConfigurationException {
		VoiceConfig m = MaryConfig.getVoiceConfig(voiceName);
		assertNotNull(m);
		assertEquals(voiceName, m.getName());
	}

	@Test
	public void hasVoiceConfigs() throws MaryConfigurationException {
		assertTrue(MaryConfig.countVoiceConfigs() > 0);
	}

	@Test
	public void hasVoiceConfigs2() throws MaryConfigurationException {
		Iterable<VoiceConfig> vcs = MaryConfig.getVoiceConfigs();
		assertNotNull(vcs);
		assertTrue(vcs.iterator().hasNext());
	}

}
