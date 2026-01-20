{
    "id": ${entity.id?c},
    "contentUrl": "${entity.contentUrl}",
    "size": ${entity.size?c}<#if entity.orphanTime??>,
    "orphanTime": ${entity.orphanTime?c}</#if>
}