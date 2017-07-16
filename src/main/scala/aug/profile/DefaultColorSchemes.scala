package aug.profile

object DefaultColorSchemes {

  val colorSchemes = {
    val list = List.newBuilder[ColorSchemeConfig]

    list += ColorSchemeConfig("default")

    list.result
  }

}
