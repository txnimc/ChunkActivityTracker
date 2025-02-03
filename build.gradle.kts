plugins {
	id("toni.blahaj")
}

blahaj {
	config {
		// yarn()
		// versionedAccessWideners()
	}
	setup {
		txnilib("1.0.22")
		forgeConfig()

		/* access Gradle's DependencyHandler
		deps.modImplementation("maven:modrinth:sodium:mc$mc-0.6.5-$loader")

		// configure Curseforge & Modrinth publish settings
		incompatibleWith("optifine")

		// add mods with Blahaj's fluent interface
		addMod("sodiumextras")
			.modrinth("sodium-extras") // override with Modrinth URL slug
			.addPlatform("1.21.1-neoforge", "neoforge-1.21.1-1.0.7")
			.addPlatform("1.21.1-fabric", "fabric-1.21.1-1.0.7") { required() } */
	}
}