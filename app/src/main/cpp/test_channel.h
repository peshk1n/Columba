#pragma once

#include <vector>
#include <queue>
#include <random>
#include <cstdint>
#include "transfer/session.h"

namespace test {

    using namespace transfer;

    struct ChannelConfig {
        float loss_rate = 0.0f;        // 0..1
        float corruption_rate = 0.0f;  // 0..1
        int delay_ms = 0;              // задержка на пакет
    };

    struct PacketWrapper {
        std::vector<Packet> packets;
        uint64_t deliver_time;
    };

    class TestChannel {
    public:
        TestChannel(const ChannelConfig& cfg)
                : config(cfg), rng(std::random_device{}()), dist(0.0f, 1.0f) {}

        void send(const std::vector<Packet>& packets, uint64_t now) {
            for (auto pkt : packets) {

                // LOSS
                if (dist(rng) < config.loss_rate) {
                    stats.lost++;
                    continue;
                }

                // CORRUPTION
                if (dist(rng) < config.corruption_rate) {
                    corrupt_packet(pkt);
                    stats.corrupted++;
                }

                PacketWrapper wrapper;
                wrapper.packets = { pkt };
                wrapper.deliver_time = now + config.delay_ms;

                queue.push(wrapper);
                stats.sent++;
            }
        }

        std::vector<Packet> receive(uint64_t now) {
            std::vector<Packet> out;

            while (!queue.empty()) {
                auto& front = queue.front();

                if (front.deliver_time > now)
                    break;

                for (auto& p : front.packets)
                    out.push_back(p);

                queue.pop();
            }

            return out;
        }

        struct Stats {
            int sent = 0;
            int lost = 0;
            int corrupted = 0;
        } stats;

    private:
        void corrupt_packet(Packet& pkt) {
            if (std::holds_alternative<DataPacket>(pkt)) {
                auto& data = std::get<DataPacket>(pkt).payload;
                if (!data.empty()) {
                    data[0] ^= 0xFF;
                }
            }
        }

        ChannelConfig config;
        std::queue<PacketWrapper> queue;

        std::mt19937 rng;
        std::uniform_real_distribution<float> dist;
    };

}