import axios from "axios"

const groupsCache = {}

const API = {

    url: () => "http://localhost:8080/api",

    get: (path) => new Promise((resolve) => axios.get(`${API.url()}/${path}`).then(it => resolve(it.data))),

    post: (path) => new Promise((resolve) => axios.post(`${API.url()}/${path}`).then(it => resolve(it.data))),

    getGroup: (id) => new Promise((resolve, reject) => {
        const cached = groupsCache[id]
        if (!cached) {
            axios.get(`${API.url()}/groups/${id}`).then(it => {
                groupsCache[id] = it.data
                resolve(it.data)
            })
        } else resolve(cached)
    }),

    searchGroups: (query) => new Promise((resolve, reject) => {
        API.get(`groups/search?q=${query ? query : ''}`).then((data) => {
            data.results.forEach(d => {
                groupsCache[d.telegramChatId] = d
            });

            resolve(data)
        })
    })

}

export default API