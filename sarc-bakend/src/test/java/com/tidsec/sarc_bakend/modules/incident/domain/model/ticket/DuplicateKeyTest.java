package com.tidsec.sarc_bakend.modules.incident.domain.model.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ChatbotScope;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;

/** FR-016, research R4: la clave del aviso de duplicado por clase de elemento. */
class DuplicateKeyTest {

	private static AffectedElement barrier(String name, String site) {
		return AffectedElement.siteWithManualLocation(SiteElementKind.VEHICLE_BARRIER, name, site, "Via", null);
	}

	@Test
	void theChatbotHasASingleKeyWhateverItsScope() {
		assertThat(DuplicateKey.of(AffectedElement.chatbot(ChatbotScope.TOTAL))).isEqualTo("CHATBOT");
		assertThat(DuplicateKey.of(AffectedElement.chatbot(null))).isEqualTo("CHATBOT");
	}

	@Test
	void aBarrierWarnsOnlyOfTheSameKindSiteAndName() {
		String same = DuplicateKey.of(barrier("Entrada", "CC Prueba Norte"));
		assertThat(DuplicateKey.of(barrier("entrada", "cc prueba norte"))).isEqualTo(same);
		assertThat(DuplicateKey.of(barrier("Salida", "CC Prueba Norte"))).as("otro nombre no avisa").isNotEqualTo(same);
		assertThat(DuplicateKey.of(barrier("Entrada", "CC Otro"))).isNotEqualTo(same);
	}

	@Test
	void theKeyIgnoresCaseAccentsAndRepeatedSpaces() {
		assertThat(DuplicateKey.of(barrier("  Entráda   Norte ", "CC  Él Bosque")))
				.isEqualTo(DuplicateKey.of(barrier("entrada norte", "cc el bosque")));
		assertThat(DuplicateKey.normalize(null)).isEmpty();
		assertThat(DuplicateKey.normalize("  Ñandú  Grande ")).isEqualTo("nandu grande");
	}

	@Test
	void theKindDistinguishesABarrierFromAnotherElementWithTheSameName() {
		AffectedElement barrier = barrier("Porton", "Sitio");
		AffectedElement other = AffectedElement.siteWithManualLocation(SiteElementKind.OTHER, "Porton", "Sitio", "Via", null);
		assertThat(DuplicateKey.of(barrier)).isNotEqualTo(DuplicateKey.of(other));
	}

	@Test
	void aReferenceEquipmentIdentifiesTheSiteInsteadOfItsName() {
		UUID reference = UUID.randomUUID();
		AffectedElement element = AffectedElement.siteWithReference(SiteElementKind.VEHICLE_BARRIER, "Entrada", reference,
				"CC Prueba Norte", "Via", null);
		assertThat(DuplicateKey.of(element)).isEqualTo("SITE:VEHICLE_BARRIER:" + reference + ":entrada");
	}

	@Test
	void aLongNameIsSummarisedToFitTheColumnWithoutLosingEquality() {
		String longName = "n".repeat(120);
		String longSite = "s".repeat(150);
		String key = DuplicateKey.of(barrier(longName, longSite));
		assertThat(key.length()).isLessThanOrEqualTo(DuplicateKey.MAX_LENGTH);
		assertThat(DuplicateKey.of(barrier(longName, longSite))).isEqualTo(key);
		assertThat(DuplicateKey.of(barrier("m".repeat(120), longSite))).isNotEqualTo(key);
	}
}
