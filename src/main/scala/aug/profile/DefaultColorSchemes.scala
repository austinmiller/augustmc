package aug.profile

object DefaultColorSchemes {

  val colorSchemes: List[ColorSchemeConfig] = {
    val list = List.newBuilder[ColorSchemeConfig]

    list += ColorSchemeConfig("default")

    list.result
  }

}
