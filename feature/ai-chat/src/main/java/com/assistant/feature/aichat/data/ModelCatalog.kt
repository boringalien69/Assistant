package com.assistant.feature.aichat.data

object ModelCatalog {

    data class CatalogEntry(
        val id: String,
        val displayName: String,
        val quantization: String,
        val fileSizeBytes: Long,
        val ramRequiredBytes: Long,
        val ramTierLabel: String,
        val speedEstimate: String,
        val downloadUrl: String,
        val fileName: String,
    )

    val entries: List<CatalogEntry> = listOf(
        CatalogEntry(
            id              = "mistral-3b-abliterated-q4km",
            displayName     = "Mistral 3B Abliterated",
            quantization    = "Q4_K_M",
            fileSizeBytes   = 2_000_000_000L,
            ramRequiredBytes = 1_800_000_000L,
            ramTierLabel    = "<=3 GB RAM",
            speedEstimate   = "10-18 tok/s",
            downloadUrl     = "https://huggingface.co/Lewdiculous/Mistral-3B-Instruct-v3-GGUF-IQ-Imatrix/resolve/main/Mistral-3B-Instruct-v3.Q4_K_M.gguf",
            fileName        = "Mistral-3B-Instruct-abliterated-Q4_K_M.gguf",
        ),
        CatalogEntry(
            id              = "mistral-3b-abliterated-q5km",
            displayName     = "Mistral 3B Abliterated",
            quantization    = "Q5_K_M",
            fileSizeBytes   = 2_400_000_000L,
            ramRequiredBytes = 2_200_000_000L,
            ramTierLabel    = "4-5 GB RAM",
            speedEstimate   = "10-16 tok/s",
            downloadUrl     = "https://huggingface.co/Lewdiculous/Mistral-3B-Instruct-v3-GGUF-IQ-Imatrix/resolve/main/Mistral-3B-Instruct-v3.Q5_K_M.gguf",
            fileName        = "Mistral-3B-Instruct-abliterated-Q5_K_M.gguf",
        ),
        CatalogEntry(
            id              = "qwen3-4b-abliterated-q4km",
            displayName     = "Qwen3 4B Abliterated",
            quantization    = "Q4_K_M",
            fileSizeBytes   = 2_800_000_000L,
            ramRequiredBytes = 2_600_000_000L,
            ramTierLabel    = "4-5 GB RAM",
            speedEstimate   = "8-14 tok/s",
            downloadUrl     = "https://huggingface.co/huihui-ai/Qwen3-4B-abliterated-GGUF/resolve/main/Qwen3-4B-abliterated-Q4_K_M.gguf",
            fileName        = "Qwen3-4B-abliterated-Q4_K_M.gguf",
        ),
        CatalogEntry(
            id              = "llama31-8b-abliterated-q4km",
            displayName     = "Llama 3.1 8B Abliterated",
            quantization    = "Q4_K_M",
            fileSizeBytes   = 4_900_000_000L,
            ramRequiredBytes = 4_500_000_000L,
            ramTierLabel    = "6-8 GB RAM",
            speedEstimate   = "4-8 tok/s",
            downloadUrl     = "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-abliterated-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-abliterated-Q4_K_M.gguf",
            fileName        = "Llama-3.1-8B-abliterated-Q4_K_M.gguf",
        ),
        CatalogEntry(
            id              = "qwen25-7b-abliterated-q4km",
            displayName     = "Qwen2.5 7B Abliterated",
            quantization    = "Q4_K_M",
            fileSizeBytes   = 4_700_000_000L,
            ramRequiredBytes = 4_300_000_000L,
            ramTierLabel    = "6-8 GB RAM",
            speedEstimate   = "4-8 tok/s",
            downloadUrl     = "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-abliterated-GGUF/resolve/main/Qwen2.5-7B-Instruct-abliterated-Q4_K_M.gguf",
            fileName        = "Qwen2.5-7B-Instruct-abliterated-Q4_K_M.gguf",
        ),
        CatalogEntry(
            id              = "mistral-small-24b-abliterated-iq4xs",
            displayName     = "Mistral Small 24B Abliterated",
            quantization    = "IQ4_XS",
            fileSizeBytes   = 13_000_000_000L,
            ramRequiredBytes = 12_000_000_000L,
            ramTierLabel    = "12+ GB RAM",
            speedEstimate   = "1-3 tok/s",
            downloadUrl     = "https://huggingface.co/bartowski/Mistral-Small-24B-Instruct-2501-abliterated-GGUF/resolve/main/Mistral-Small-24B-Instruct-2501-abliterated-IQ4_XS.gguf",
            fileName        = "Mistral-Small-24B-abliterated-IQ4_XS.gguf",
        ),
    )

    /**
     * Returns the best-fit model for the available RAM.
     * Conservative: use availMem * 0.6 as effective budget.
     * Best fit = highest tier whose RAM need <= effective budget.
     */
    fun bestFit(availableRamBytes: Long): CatalogEntry? {
        val effectiveBudget = (availableRamBytes * 0.6).toLong()
        return entries
            .filter { it.ramRequiredBytes <= effectiveBudget }
            .maxByOrNull { it.ramRequiredBytes }
    }
}
